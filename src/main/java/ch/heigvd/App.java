package ch.heigvd;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Random;


public class App extends Thread {

    private final String propertiesFileName = "site.properties";

    private int number;
    private Site electedSite;

    private Gestionnaire gestionnaire;

    Random random;

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
        this.random = new Random();

        Properties properties = new Properties();
        try {
            properties = getSiteProperties();
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }

        List<Site> sites = getAllSite(properties);
        int timeOutQuittance = getTimeOutQuittance(properties);

        System.out.println("App: affichage des sites");
        for (Site site : sites) {
            System.out.println(site);
        }

        gestionnaire = new Gestionnaire(sites, number, timeOutQuittance);
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
        DatagramPacket packetPing;
        byte[] messagePing;
        byte[] messageResponsePing;

        try {
            System.out.println("Applicatif:: Création du socket de récéption pour le site n°" + number);
            socketPing = new DatagramSocket();

            while (true) {
                System.out.println("Applicatif:: récupération de l'élu");
                electedSite = gestionnaire.getElu();
                System.out.println("Applicatif:: L'élu est : " + electedSite.getNumber());

                try {
                    // Dans le cas ou nous sommes l'élu, on attend simplement un moment et on recommence
                    if (electedSite.getNumber() == number) {
                        sleep(3000 + random.nextInt(2000));
                        continue;
                    }

                    System.out.println("Applicatif:: Création du Ping");
                    messagePing = MessageUtil.creationPing();
                    packetPing = new DatagramPacket(messagePing, messagePing.length, electedSite.getIp(), electedSite.getPort());

                    socketPing.send(packetPing);

                    // On crée le paquet de réponse
                    packetPing = new DatagramPacket(new byte[1], 1);

                    // On pose une limite de temps sur la réception du reçu
                    System.out.println("Applicatif:: récupération de l'écho");
                    socketPing.setSoTimeout(200);
                    socketPing.receive(packetPing);

                    // Si on a reçu la réponse à temps, alors on vérifie que c'est bien une quittance
                    messageResponsePing = new byte[packetPing.getLength()];
                    System.arraycopy(packetPing.getData(), packetPing.getOffset(), messageResponsePing, 0, packetPing.getLength());

                    if (MessageUtil.getTypeOfMessage(messageResponsePing) == MessageUtil.TypeMessage.QUITTANCE) {
                        System.out.println("L'élu est toujours en ligne");
                        sleep(3000 + random.nextInt(2000));
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
            System.err.println("Applicatif:: Echec de la création du socket pour le site n°" + number);
            e.printStackTrace();
        }
    }

    private List<Site> getAllSite(Properties properties) {
        List<Site> sites = new ArrayList<>();

        // get the property value and print it out
        String number_site = properties.getProperty("totalSiteNumber");
        System.out.println(number_site);

        String siteAddress;
        InetAddress siteIP;
        int sitePort;

        try {
            for (int i = 0; i < Integer.parseInt(number_site); ++i) {
                siteAddress = properties.getProperty(String.valueOf(i));
                String[] values = siteAddress.split(":");
                siteIP = InetAddress.getByName(values[0]);
                sitePort = Integer.parseInt(values[1]);
                sites.add(new Site(i, siteIP, sitePort));
            }
        } catch (Exception e) {
            System.out.println("Exception: " + e);
        }

        return sites;
    }

    private int getTimeOutQuittance(Properties properties) {
        // get the property value and print it out
        String timeOutQuittanceString = properties.getProperty("TIMEOUT_QUITTANCE");
        return Integer.parseInt(timeOutQuittanceString);
    }

    private Properties getSiteProperties() throws IOException {
        Properties properties = new Properties();
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream(propertiesFileName);

        if (inputStream != null) {
            properties.load(inputStream);
        } else {
            throw new FileNotFoundException("property file '" + propertiesFileName + "' not found in the classpath");
        }

        return properties;
    }

    public static void main(String[] args) {
        new App(args).start();
    }
}
