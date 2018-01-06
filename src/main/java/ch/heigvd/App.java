package ch.heigvd;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.net.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Random;

/**
 * Hello world!
 */
public class App extends Thread {
    private int numero;
    private Site elu;

    private Gestionnaire gestionnaire;

    private DatagramSocket socket;
    Random r;


    public App(String[] args) {
        r = new Random();

        if (args.length != 1) {
            System.err.println("Invalid argument, you need to pass a site number");
            System.exit(1);
        }

        this.numero = Integer.parseInt(args[0]);

        List<Site> sites = getAllSite();

        System.out.println("App: affichage des sites");
        for (Site site : sites) {
            System.out.println(site);
        }

        gestionnaire = new Gestionnaire(sites, numero);
        gestionnaire.start();

    }

    public void run() {
        System.out.println("Applicatif:: démarage des permières élections");
        gestionnaire.commencerElection();

        try {
            System.out.println("Applicatif:: Création du socket de récéption pour le site n°" + numero);
            socket = new DatagramSocket();
        } catch (SocketException e) {
            System.err.println("Applicatif:: Echeq de la création du socker pour le site n°" + numero);
            e.printStackTrace();
        }

        DatagramPacket paquet;

        while (true) {
            System.out.println("Applicatif:: récupération de l'élu");
            elu = gestionnaire.getElu();
            System.out.println("Applicatif:: L'élu est : " + elu.getNumero());

            try {
                if (elu.getNumero() == numero) {
                    sleep(3000 + r.nextInt(2000));
                    continue;
                }
                System.out.println("Applicatif:: Création du Ping");
                byte[] messagePing = MessageUtil.creationPing();
                paquet = new DatagramPacket(messagePing, messagePing.length, elu.getIp(), elu.getPort());

                socket.send(paquet);

                paquet = new DatagramPacket(new byte[1], 1);

                // On pose une limite de temps sur la réception du reçu
                System.out.println("Applicatif:: récupération de l'écho");
                socket.setSoTimeout(200);
                socket.receive(paquet);


                byte[] message = new byte[paquet.getLength()];
                System.arraycopy(paquet.getData(), paquet.getOffset(), message, 0, paquet.getLength());

                if (MessageUtil.getTypeOfMessage(message) == MessageUtil.TypeMessage.ECHO) {
                    System.out.println("L'élu est toujours en ligne");
                    sleep(3000 + r.nextInt(2000));
                }

            } catch (SocketTimeoutException e) {
                System.out.println("Applicatif:: l'élu est hs, démarrage d'élections");
                gestionnaire.commencerElection();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private List<Site> getAllSite() {
        List<Site> sites = new ArrayList<Site>();

        InputStream inputStream;

        try {
            Properties prop = new Properties();
            String propFileName = "site.properties";

            inputStream = getClass().getClassLoader().getResourceAsStream(propFileName);

            if (inputStream != null) {
                prop.load(inputStream);
            } else {
                throw new FileNotFoundException("property file '" + propFileName + "' not found in the classpath");
            }

            // get the property value and print it out
            String number_site = prop.getProperty("number_site");
            System.out.println(number_site);

            String siteAddress;
            InetAddress siteIP;
            int sitePort;

            for (int i = 0; i < Integer.parseInt(number_site); ++i) {
                siteAddress = prop.getProperty(String.valueOf(i));
                String[] values = siteAddress.split(":");
                siteIP = InetAddress.getByName(values[0]);
                sitePort = Integer.parseInt(values[1]);
                sites.add(new Site(i, siteIP, sitePort));
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
