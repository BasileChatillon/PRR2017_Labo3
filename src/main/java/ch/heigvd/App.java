package ch.heigvd;

import ch.heigvd.util.Message;
import ch.heigvd.util.TypeMessage;

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

    private GestionnaireElection gestionnaireElection;

    Random random;

    /**
     * Constructeur qui permet d'initialiser le gestionnaire ainsi que les différents éléments nécessaires au bon
     * fonctionnement du thread
     *
     * @param args Le numéro du site
     */
    public App(String[] args) {
        // Vérification que l'utilisateur a bien entré un paramètre
        if (args.length != 1) {
            System.err.println("Invalid argument, you need to pass a site number");
            System.exit(1);
        }

        // Récupération du paramètre
        this.number = Integer.parseInt(args[0]);
        this.random = new Random();

        // Récupération des propriétés dans le but d'y extraire des informations
        Properties properties = new Properties();
        try {
            properties = getSiteProperties();
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }

        // Extraction d'infos depuis les propriétés
        List<Site> sites = getAllSite(properties);
        int timeOutQuittance = getTimeOutQuittance(properties);
        int timeOutElection = getTimeOutElection(properties);

        System.out.println("App: affichage des sites");
        for (Site site : sites) {
            System.out.println(site);
        }

        // Création et lancement du gestionnaireElection
        gestionnaireElection = new GestionnaireElection(sites, number, timeOutQuittance, timeOutElection);
        gestionnaireElection.start();
    }

    /**
     * Le thread va de temps en temps récupérer l'élu et tenter de le contacter pour voir s'il est en ligne.
     * Si ce n'est pas le cas, il va lancer une nouvelle élection.
     * S'il est en ligne, il attendra avant de le contacter à nouveau.
     */
    public void run() {
        final int MIN_TIME_SLEEPING = 5000;
        final int MAX_TIME_SLEEPING = 8000;
        System.out.println("Applicatif:: démarrage des peremières élections");

        gestionnaireElection.startElection();

        DatagramSocket socketPing;
        DatagramPacket packetPing;
        byte[] messagePing;
        byte[] messageResponsePing;

        try {
            System.out.println("Applicatif:: Création du socket de réception pour le site n°" + number);
            socketPing = new DatagramSocket();

            while (true) {
                System.out.println("Applicatif:: Récupération de l'élu");
                electedSite = gestionnaireElection.getElu();
                System.out.println("Applicatif:: L'élu est : " + electedSite.getNumber());

                try {
                    // Dans le cas où nous sommes l'élu, on attend simplement un moment et on recommence
                    if (electedSite.getNumber() == number) {
                        sleep(MIN_TIME_SLEEPING + random.nextInt(MAX_TIME_SLEEPING - MIN_TIME_SLEEPING));
                        continue;
                    }

                    System.out.println("Applicatif:: Création du Ping");
                    messagePing = Message.createPing();
                    packetPing = new DatagramPacket(messagePing, messagePing.length, electedSite.getIp(), electedSite.getPort());

                    socketPing.send(packetPing);

                    // On crée le paquet de réponse grâce à un buffer
                    packetPing = new DatagramPacket(new byte[1], 1);

                    // On pose une limite de temps sur la réception du reçu pour permettre de vérifier si l'élu est en ligne
                    System.out.println("Applicatif:: Récupération de l'écho");
                    socketPing.setSoTimeout(200);
                    socketPing.receive(packetPing);

                    // Si on a reçu la réponse à temps, alors on vérifie que c'est bien une quittance
                    messageResponsePing = new byte[packetPing.getLength()];
                    System.arraycopy(packetPing.getData(), packetPing.getOffset(), messageResponsePing, 0, packetPing.getLength());

                    // On vérifie également que le message envoyé par l'élu est bien une quittance
                    if (Message.getTypeOfMessage(messageResponsePing) == TypeMessage.QUITTANCE) {
                        System.out.println("L'élu est toujours en ligne");
                        sleep(MIN_TIME_SLEEPING + random.nextInt(MAX_TIME_SLEEPING - MIN_TIME_SLEEPING));
                    }

                } catch (SocketTimeoutException e) {
                    // Si on a pas reçu la quittance à temps, on lance une élection
                    System.out.println("Applicatif:: L'élu est HS, démarrage d'élections");
                    gestionnaireElection.startElection();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } catch (SocketException e) {
            System.err.println("Applicatif:: Echec de la création du socket pour le site n°" + number);
            e.printStackTrace();
        }
    }

    /**
     * Fonction qui permet de récupérer tous les sites contenu dans le ficher site.properties.
     *
     * @param properties L'instance de properties dans laquelle sont stockées les différentes propriétés à récupérer
     * @return La liste des sites.
     */
    private List<Site> getAllSite(Properties properties) {
        List<Site> sites = new ArrayList<>();

        // On récupère le nombre de site total
        String number_site = properties.getProperty("totalSiteNumber");
        System.out.println(number_site);

        String siteAddress;
        InetAddress siteIP;
        int sitePort;

        try {
            // On parcourt ensuite tous les sites dans le fichier de propriétés et on récupère leurs informations
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

    /**
     * Méthode qui permet de récupérer la valeur du timeOut (en [ms]) de la quittance depuis des propriétés
     *
     * @param properties Les propriétés
     * @return La valeur en milliseconde du timeOut
     */
    private int getTimeOutQuittance(Properties properties) {
        String timeOutQuittanceString = properties.getProperty("TIMEOUT_QUITTANCE");
        return Integer.parseInt(timeOutQuittanceString);
    }

    /**
     * Méthode qui permet de récupérer la valeur du timeOut (en [ms]) d'une élection depuis des propriétés
     *
     * @param properties Les propriétés
     * @return La valeur en milliseconde du timeOut
     */
    private int getTimeOutElection(Properties properties) {
        String timeOutElectionString = properties.getProperty("TIMEOUT_ELECTION");
        return Integer.parseInt(timeOutElectionString);
    }

    /**
     * Permet de récupérer une instance de la class Properties du fichier de propriétés sites.properties.
     * Cela permet ensuite de passer cette instance à différentes méthode pour y récupérer différents élements
     *
     * Lance une exception si le ficher .properties n'est pas trouvé
     *
     * @return un instance de Properties
     * @throws IOException
     */
    private Properties getSiteProperties() throws IOException {
        Properties properties = new Properties();
        // On récupère un stream
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream(propertiesFileName);

        // Si on a bien récupéré le stream, on tente de charger l'instance Properties
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
