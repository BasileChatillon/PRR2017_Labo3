/**
 * Basile Châtillon & Nicolas Rod
 *
 * Laboratoire de PRR n°3 : Election en anneau avec panne
 *
 * --- Marche à suivre ---
 * Pour lancer les différente sites, il suffit de passer le numéro du site en argument du programme.
 * Le numéro doit être compris entre 0 et 3 vu qu'il y a 4 sites dans cette exemple.
 * Il n'est pas possible de lancer deux fois le même site.
 *
 * --- Conception ---
 * Comme la donnée du laboratoire le demandait, nous avons implémenté deux threads pour faire ce laboratoire.
 * Le premier, le thread App, se charge de vérifier de temps en temps que le site élu n'est pas en panne.
 * Si celui-ci constate qu'il est tombé en panne, il se chargera de communiquer à son gestionnaire de commencer
 * une nouvelle élection.
 * Le deuxième, le thread GestionnaireElection, se charge quand à lui d'implémenter le protocole de
 * l'élection en anneau. Il se chargera donc d'implémenter la logique lors de la réception de message
 * ainsi que l'envoie des messages des bons messages aux sites voisins.
 * Pour pouvoir procéder à une élection en anneau avec panne, nous sommes partis du principe que chaque site
 * connait l'adresse de tous les autres sites. Nous avons stocké ces adresses dans un fichier de ressource
 * site.properties
 *
 * Nous avons également décidé d'ajouter une classe Site qui modélise un site et qui contient toutes les coordonnées
 * nécessaires à l'établissement d'une connexion UDP point à point (une IP et un port).
 *
 * Nous avons finalament un package "util" qui contient des classes nous permettant de construire les différents
 * messages à envoyer selon les informations et également de récupérer les diverses informations contenues
 * dans les messages.
 * Il contient également un Enum TypeMessage qui recensse les différents messages que nous utilisons dans cette
 * application. Chaque Type de message est également associé à un byte.
 *
 *
 * Pour communiquer avec le thread gestionnaire, l'applicatif va simplement appeler ses méthodes. En effet, il possède
 * un instance de celui-ci.
 *
 * Le thread App, pour contacter l'élu, va d'abord le récupérer auprès de son gestionnaire. Si le gestionnaire
 * n'est pas en train de faire une élection, il lui fournira instantanément. Dans le cas contraire, le thread applicatif
 * sera bloqué jusqu'à la fin de l'élection OU jusqu'à ce que l'attente dépasse un time out. Dès lors une nouvelle
 * élection sera faite et le thread applicatif sera à nouveau mis en attente.
 * Une fois l'adresse de l'élu récupérée, il pourra le connecter via un ping et attendre la quittance de l'élu.
 * Si la quittance n'arrive pas avant un temps imparti, alors l'applicatif considère que le site élu est en panne
 * et il demande à son gestionnaire de commencer une nouvelle élection.
 * Dans le cas où le site élu a bien envoyé sa quittance, nous endormons le thread applicatif pour qu'il évite
 * de noyer le réseau de message. Le temps d'attente est aléatoire pour éviter que le site élu recoive tous les
 * messages de ping en même temps.
 *
 * Le gestionnaire aura lui un socket ouvert avec son port. Celui-ci lui permettra de se faire contacter par
 * 1. les autres gestionnaires des autres sites pour procéder aux échanges des annonces ainsi que des résultats
 * 2. Les parties applicatives des autres sites dans le cas où il est élu (car il doit répondre au message de ping
 * des autres sites.
 *
 *
 * Les messages envoyés aux sites sont formés de différentes manières selon le type de message. Néanmoins, le premier
 * byte de chaque message correspond au type du message.
 * Les message ANNONCE sont donc composés de 1 byte représentant le type. Ensuite nous aurons autant de fois 8 bytes
 * que de site qui ont vu le message. Un message d'annonce comprend le numéro et l'aptitude de tous les sites qui ont
 * déjà recu le message d'annonce.
 * Nous avons décidé que le numéro de site sera un int, ce qui permet d'avoir plus de marge vis-à-vis du nombre
 * de sites. L'aptitude étant également un int, (le numéro de port peut être relativement
 * grand) nous arrivons à un totale de 4bytes + 4bytes = 8 bytes par site.
 *
 * Les messages RESULTAT sont composés de 1 byte représantant le type. 4 bytes représentant le numéro du site élu
 * (car c'est un int) puis la liste des numéros des sites qui ont vu ce message. Donc autant de fois 4 bytes qu'il y a
 * de site.
 *
 * Finalement nous avons les messages du PING et de la QUITTANCE qui sont eux composés que d'un seul byte, le
 * type du message.
 *
 *
 * Une partie compliquée a été d'implémenter la gestion des quittances. Celle du ping est facile à implémenter vu
 * que c'est la partie applicative qui va s'occuper de la récéptionner.
 * Lorsque le gestionnaire reçoit un message de ping, il lui suffit simplement de répondre à ce message par une
 * quittance.
 *
 * La gestion des quittances entre gestionnaire se fait directement dans la méthode d'envoie. Lorsqu'on désire
 * envoyer un message, nous passerons par la méthode envoie. Celle-ci va ouvrir un nouveau socket et l'utiliser
 * pour envoyer le message et attendre la quittance. Si la quittance n'est pas reçue après un temps imparti, alors
 * on tente de communiquer au voisin suivant. On répète l'opération jusqu'à trouver un site qui n'est pas en panne
 * ou lorsqu'on a fait le tour de l'anneau. Cela signifie qu'aucun autre site n'est actif et que nous sommes donc
 * l'élu par défaut. Du coup, dès qu'un gestionnaire reçoit un message ANNONCE ou RESULTAT, la première chose qu'il
 * fera est d'envoyer la quittance en réponse au message.
 * Nous avons décidé d'ouvrir un nouveau socket lors de l'envoi du message et de ne pas réutiliser le socket utilisé
 * pour la récéption car cela permet d'éviter d'avoir à différencier les messages de quittance. Vu que le gestionnaire
 * répond au message reçu, nous somme sûr que c'est la réponse au bon message.
 * À chaque envoi de message, nous tentons toujours de contacter notre voisin direct en premier puis
 * les autres ensuite. C'est-à-dire que si lors de l'envoi d'un message il s'avère qu'il est en panne, lors du prochain
 * message nous tentons quand même de le lui envoyer.
 *
 *
 * --- Tests ---
 * Tout premièrement, nous avons testé de lancer les différents sites un à un avec un intervale suffisament grand pour
 * qu'ils aient le temps de faire une élection. Cela a permis de vérifier que lors de la mise en service d'un site
 * celui-ci lance bien une élection, et que le bon site devienne l'élu.
 *
 * Nous avons ensuite tester les pannes.
 * La première est de faire tomber un site non-élu en panne lorsque l'élection n'est pas terminée (il suffit d'arrêter le
 * processus). On constate donc bien que tous les autres sites continues à vivre paisiblement car le site élu n'est pas
 * en panne.
 * Nous avons ensuite répété la même démarche, mais cette fois si en arrêtant le site élu.
 * Alors, on a pu constater qu'un des site non-élu remarque que le site élu ne fonctionne plus et provoque une élection.
 * La nouvelle élection se déroule sans encombre.
 * Nous avons également constaté que lorsque le site élu tombe en panne, les sites non-élus lancent parfois
 * chacun de leur côté une élection. Une seule de celle-ci finit par aboutir, ce qui concorde avec la spécification
 * du protocole.
 *
 * Un autre test a été de faire tomber en panne un site juste après que celui-ci aie envoyé une QUITTANCE après avoir
 * reçu un message d'annonce. (il reçoit un message d'annonce, il envoie la quittance à l'émetteur et tombe en panne
 * AVANT d'avoir pu envoyer le nouveau message d'annonce).
 * L'élection est donc gelée car le site émetteur ayant reçu une quittance de son voisin, ne pense pas qu'il est
 * en panne.
 * C'est grâce au timeOut des élections que nous surmontons ce problème. On peut bien constater qu'après que l'élection
 * aie durée plus de X temps, alors on en relance simplement une nouvelle.
 *
 * Le dernier cas ressemble au précédent. Un site reçoit un message d'annonce, envoie la quittance, transmet
 * le message au site suivant et crash. Ce problème est géré, car lorsqu'un nouveau message lui sera envoyé,
 * on pourra voir qu'il ne renvoie pas de quittance et qu'il est donc en panne.
 * S'il s'avère que c'est le site élu, ça veut dire que les sites ont élu un site en panne sans le savoir
 * Ce cas est géré car lorsque les parties applicatives récupéreront l'élu, elles verront directement que le site est
 * en panne et relanceront une élection.
 * S'il s'avère que le site en panne n'est pas l'élu, cela n'a pas d'importance.
 */


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
