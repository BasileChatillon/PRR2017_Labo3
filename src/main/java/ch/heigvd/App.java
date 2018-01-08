package ch.heigvd;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.net.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Random;


public class App extends Thread {
    private int number;
    private Site electedSite;

    private Gestionnaire gestionnaire;

    Random r;

    /**
     * Constructeur qui permet d'initialiser le gestionaire ainsi que les différents éléments nécessaires au bon
     * fonctionnement du thread
     *
     * @param args La numéro du site
     */
    public App(String[] args) {
        if (args.length != 1) {
            System.err.println("Invalid argument, you need to pass a site number");
            System.exit(1);
        }

        this.number = Integer.parseInt(args[0]);
        this.r = new Random();

        List<Site> sites = getAllSite();

        System.out.println("App: affichage des sites");
        for (Site site : sites) {
            System.out.println(site);
        }

        gestionnaire = new Gestionnaire(sites, number);
        gestionnaire.start();
    }

    /**
     * Le thread va de temps en temps récupérer l'élu et tenter de le contacter pour voir s'il est en ligne.
     * Si ce n'est pas le cas, il va lancer une nouvelle élection
     */
    public void run() {
        System.out.println("Applicatif:: démarage des permières élections");
        gestionnaire.statElection();

        DatagramSocket socketPing;

        try {
            System.out.println("Applicatif:: Création du socket de récéption pour le site n°" + number);
            socketPing = new DatagramSocket();

            DatagramPacket packetPing;

            while (true) {
                System.out.println("Applicatif:: récupération de l'élu");
                electedSite = gestionnaire.getElu();
                System.out.println("Applicatif:: L'élu est : " + electedSite.getNumber());

                try {
                    // Dans le cas ou nous sommes l'élu, on attend simplement un moment et on recommence
                    if (electedSite.getNumber() == number) {
                        sleep(3000 + r.nextInt(2000));
                        continue;
                    }

                    System.out.println("Applicatif:: Création du Ping");
                    byte[] messagePing = MessageUtil.creationPing();
                    packetPing = new DatagramPacket(messagePing, messagePing.length, electedSite.getIp(), electedSite.getPort());

                    socketPing.send(packetPing);

                    // On crée le paquet de réponse
                    packetPing = new DatagramPacket(new byte[1], 1);

                    // On pose une limite de temps sur la réception du reçu
                    System.out.println("Applicatif:: récupération de l'écho");
                    socketPing.setSoTimeout(200);
                    socketPing.receive(packetPing);

                    // Si on a reçu la réponse à temps, alors on vérifie que c'est bien une quittance
                    byte[] message = new byte[packetPing.getLength()];
                    System.arraycopy(packetPing.getData(), packetPing.getOffset(), message, 0, packetPing.getLength());

                    if (MessageUtil.getTypeOfMessage(message) == MessageUtil.TypeMessage.QUITTANCE) {
                        System.out.println("L'élu est toujours en ligne");
                        sleep(3000 + r.nextInt(2000));
                    }

                } catch (SocketTimeoutException e) {
                    // Si on a pas reçu la quittance à temps, on lance une élection
                    System.out.println("Applicatif:: l'élu est hs, démarrage d'élections");
                    gestionnaire.statElection();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } catch (SocketException e) {
            System.err.println("Applicatif:: Echeq de la création du socker pour le site n°" + number);
            e.printStackTrace();
        }
    }

    /**
     * Fonction qui permet de récuprer tous les sites contenu dans le ficher site.properties.
     * @return La liste des sites.
     */
    private List<Site> getAllSite() {
        List<Site> sites = new ArrayList<>();

        InputStream inputStream;

        try {
            Properties prop = new Properties();
            String propFileName = "site.properties";
            // On va aller scanner le fichier site.properties pour récupérer les informations qui sont dedans
            inputStream = getClass().getClassLoader().getResourceAsStream(propFileName);

            if (inputStream != null) {
                prop.load(inputStream);
            } else {
                throw new FileNotFoundException("property file '" + propFileName + "' not found in the classpath");
            }

            // On récupère le nombre de site, qu'on affiche
            String totalSiteNumber = prop.getProperty("totalSiteNumber");
            System.out.println("Nombre de site total" + totalSiteNumber);

            String adressSite;
            InetAddress ipSite;
            int portSite;

            // On parcourt ensuite tous les sites pour récupérer leurs informations et les stocker
            for (int i = 0; i < Integer.parseInt(totalSiteNumber); ++i) {
                adressSite = prop.getProperty(String.valueOf(i));
                String[] values = adressSite.split(":");
                ipSite = InetAddress.getByName(values[0]);
                portSite = Integer.parseInt(values[1]);
                sites.add(new Site(i, ipSite, portSite));
            }

            inputStream.close();

        } catch (Exception e) {
            System.out.println("Exception: " + e);
        }

        return sites;
    }

    public static void main(String[] args) {
        new App(args).start();
    }
}
