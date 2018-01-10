package ch.heigvd;

import ch.heigvd.util.Message;
import ch.heigvd.util.TypeMessage;

import java.io.IOException;
import java.net.*;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class GestionnaireElection extends Thread {
    /**
     * Un enum qui premet de définir l'étape en cours
     */
    private enum StageInProgress {
        ANNONCE,
        RESULTAT,
        FINI;
    }

    private List<Site> sites; // Une Liste contenant tous les sites existant dans l'anneau
    private int numberSiteTot; // le nombre de site dans l'anneau

    private int me, // Notre numéro de site
            neighbor, // le site se trouvant après nous, soit celui à qui ont doit envoyer les messages
            siteElected; // Le site qui sera élu

    private int myAptitude; // La valeur de mon aptitude

    private final int TIMEOUT_QUITTANCE;
    private final int TIMEOUT_ELECTION;

    private StageInProgress stageInProgress; // l'étape en cours
    private Object mutex; // le mutex qui permet d'éviter que l'applicatif accède à l'élu lorsqu'il n'est pas encore élu


    private DatagramSocket socketReception; // Le socket qui va permettre la réception des messages venant des autres sites


    GestionnaireElection(List<Site> sites, int me, int TIMEOUT_QUITTANCE, int TIMEOUT_ELECTION) {
        this.sites = sites;
        this.numberSiteTot = sites.size();

        this.me = me;
        this.neighbor = (me + 1) % sites.size(); // permet de récupérer le voisin suivant
        this.siteElected = me; // C'est une sécurité pour si jamais le mutex ne marcherait pas bien

        this.myAptitude = sites.get(me).getAptitude();

        this.TIMEOUT_QUITTANCE = TIMEOUT_QUITTANCE;
        this.TIMEOUT_ELECTION = TIMEOUT_ELECTION;

        this.stageInProgress = StageInProgress.FINI;
        this.mutex = new Object();

        try {
            System.out.println("GestionnaireElection:: Création du socketReception de réception pour le site n°" + me);
            // Ouvture du socket qui permettra au gestionnaire de recevoir des messages
            socketReception = new DatagramSocket(sites.get(me).getPort());
        } catch (SocketException e) {
            System.err.println("GestionnaireElection:: Echeck de la création du socker pour le site n°" + me);
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        while (true) {
            // La taille du tampon est la taille du plus long message possible, soit quand tous les sites ont répondu à l'annonce.
            int sizeMessageMax = 1 + 2 * 4 * numberSiteTot;

            DatagramPacket packetReceived = new DatagramPacket(new byte[sizeMessageMax], sizeMessageMax);

            // Attente de réception d'un message
            try {
                socketReception.receive(packetReceived);

                // Récupération du message
                byte[] message = new byte[packetReceived.getLength()];
                System.arraycopy(packetReceived.getData(), packetReceived.getOffset(), message, 0, packetReceived.getLength());

                switch (Message.getTypeOfMessage(message)) {
                    case ANNONCE:
                        // On commence par envoyer la quittance à l'émetteur du message
                        sendQuittance(packetReceived.getAddress(), packetReceived.getPort());

                        if (me == 3) {
                            int i = 1 / 0;
                        }
                        System.out.println("GestionnaireElection:: Reception d'un message d'annonce");
                        // on récupère les site qui ont émis une annonce en les mappant avec leur aptitude
                        Map<Integer, Integer> siteAnnonces = Message.extractAnnonce(message);

                        if (siteAnnonces.containsKey(me)) {
                            System.out.println("GestionnaireElection:: Fin de la boucle, on détermine l'élu");

                            // On récupère le site qui à la meilleur atptitude et ensuite par rapport à l'IP
                            // On va les trier dans un premier temps en fonction de celui qui a la plus grande aptitude
                            // Puis en fonction de celui qui a la plus petite IP en cas d'égalité
                            siteElected = siteAnnonces.entrySet().stream()
                                    .sorted(Map.Entry.<Integer, Integer>comparingByValue().reversed()
                                            .thenComparing(u -> sites.get(u.getKey()).getLastByteOfIp()))
                                    .limit(1)
                                    .map(u -> u.getKey())
                                    .collect(Collectors.toList())
                                    .get(0);

                            // On prépare le message de résultat
                            byte[] messageResult = Message.createResultat(siteElected, me);
                            System.out.println("GestionnaireElection:: Election terminée, le site elu est " + siteElected);
                            sendMessage(messageResult);

                            // l'étape est maintenant les résultats
                            stageInProgress = StageInProgress.RESULTAT;
                        } else {
                            // Si on est pas dans la liste, la phase de l'annonce est toujours en cours
                            // On récupère le vieux message d'annonce pour construire le nouveau
                            byte[] messageAnnonce = Message.createAnnonce(message, me, myAptitude);
                            System.out.println("GestionnaireElection:: Election non terminée, on s'ajoute à la liste");

                            sendMessage(messageAnnonce);

                            stageInProgress = StageInProgress.ANNONCE;
                        }
                        break;

                    case RESULTAT:
                        // On commence par envoyer la quittance à l'émetteur du message
                        sendQuittance(packetReceived.getAddress(), packetReceived.getPort());

                        System.out.println("GestionnaireElection:: Reception d'un message de résultat");
                        List<Integer> siteResult = Message.extractSitesFromResult(message);
                        int elu = Message.extractElectedFromResult(message);

                        if (siteResult.contains(me)) {
                            // Si le résultat à fait le tour de l'anneau, alors on est dans la liste et le siteElected est le bon
                            stageInProgress = StageInProgress.FINI;
                            endElection();

                        } else if (stageInProgress == StageInProgress.RESULTAT && elu != siteElected) {
                            // Dans le cas ou l'on reçoit un message de résultat après en avoir eu un autre résultat
                            // Il y a une erreur alors on relance une élection
                            byte[] messageAnnonce = Message.createAnnonce(me, myAptitude);
                            System.out.println("GestionnaireElection:: On recommence une élection");

                            sendMessage(messageAnnonce);

                            stageInProgress = stageInProgress.ANNONCE;
                        } else if (stageInProgress == StageInProgress.ANNONCE) {
                            // Dans le cas il y a une annonce et qu'on recoit un message de résultat
                            siteElected = elu; // On élit le site

                            // On crée un message de résultat en prenant l'ancien et en s'ajoutant dedans.
                            byte[] messageResult = Message.createResultat(message, me);
                            System.out.println("GestionnaireElection:: Election terminée, annonce du résultat");
                            sendMessage(messageResult);

                            // l'étape est maintenant les résultats
                            stageInProgress = StageInProgress.RESULTAT;
                            endElection();
                        }
                        break;

                    case PING:
                        System.out.println("GestionnaireElection:: Reception d'un message de ping");
                        sendQuittance(packetReceived.getAddress(), packetReceived.getPort());
                        break;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void endElection() {
        synchronized (mutex) {
            mutex.notifyAll();
        }
    }

    /**
     * Permet d'envoyer un message de quittance
     *
     * @param ip   L'adresse du site de destination
     * @param port Le port du site de destination
     */
    private void sendQuittance(InetAddress ip, int port) {
        byte[] message = Message.createQuittance();
        DatagramPacket packetQuittance = new DatagramPacket(message, message.length, ip, port);

        try {
            socketReception.send(packetQuittance);
        } catch (IOException e) {
            System.err.println("GestionnaireElection:: échec d'envoi de la quittance");
            e.printStackTrace();
        }
    }

    /**
     * Permet d'envoyer un message à notre voisin.
     * Si le voisin n'est pas disponible, le même message sera envoyé au voisin suivant. et ainsi de suite jusqu'à
     * avoir trouvé un voisin fonctionnel.
     * Dans le cas ou aucun voisin n'est trouvé, On s'élit nous même et on mets fin aux élections
     *
     * @param message Le message à envoyer
     */
    private void sendMessage(byte[] message) {
        InetAddress ip; // L'adresse utilisée pour envoyer le message
        int port; // Le port utilisé pour envoyer le message
        int workingNeighbor = neighbor;

        // Création du socket et du packet que l'on va utiliser
        DatagramSocket socketQuittance;
        DatagramPacket packetMessage;

        try {
            socketQuittance = new DatagramSocket();

            boolean quittanceReceived = false;
            boolean lapAround = false;

            // Tant qu'on a pas recu la réponse et qu'on a pas fait un tour de l'anneau, on va contacter le voisin suivant
            while (quittanceReceived == false && lapAround != true) {
                // On commence par récupérer les infos du site à contacter
                ip = sites.get(workingNeighbor).getIp();
                port = sites.get(workingNeighbor).getPort();

                try {
                    // On crée le packet et on l'envoie au site
                    packetMessage = new DatagramPacket(message, message.length, ip, port);
                    socketQuittance.send(packetMessage);

                    // On met le timeOut à 200 millisecondes et on attend la réponse du site
                    socketQuittance.setSoTimeout(TIMEOUT_QUITTANCE);
                    socketQuittance.receive(packetMessage);

                    // Si on a recu un message dans le temps imparti, on vérifie quand même que c'est le bon message
                    if (Message.getTypeOfMessage(packetMessage.getData()) == TypeMessage.QUITTANCE) {
                        quittanceReceived = true;
                    }
                } catch (SocketTimeoutException e) {
                    /* Si il y a eu un timeout, ça veut dire que le site voisin n'est pas disponible, donc qu'on va demander
                     * au voisin d'après */
                    System.out.println("GestionnaireElection:: neighbor n°" + workingNeighbor + " est inactif");
                    workingNeighbor = (workingNeighbor + 1) % numberSiteTot;

                    // Dans le cas ou on a fait un tour, on finit les élections car il n'y a que nous
                    if (workingNeighbor == me) {
                        stageInProgress = StageInProgress.FINI;
                        lapAround = true;
                        siteElected = me;
                        // Ensuite on finit les élections
                        endElection();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } catch (SocketException e) {
            System.err.println("GestionnaireElection:: problème lors de l'ouverture du socketReception d'envoie du message");
            e.printStackTrace();
        }
    }

    /**
     * Permet de commencer les éléctions en envoyant un message d'annonce
     */
    public void startElection() {
        System.out.println("GestionnaireElection:: début des élections");

        stageInProgress = StageInProgress.ANNONCE;
        byte[] message = Message.createAnnonce(me, myAptitude);

        sendMessage(message);
    }

    /**
     * Permet de récupérer le site élu.
     * Cette fonction est blocante tant qu'une élection est en cours.
     *
     * @return le site élu
     */
    public Site getElu() {
        // On vérifie qu'on est pas en train de faire une annonce avant de récupérér les infos du site

        synchronized (mutex) {
            while (stageInProgress == StageInProgress.ANNONCE) {
                try {
                    mutex.wait(TIMEOUT_ELECTION);
                    if (stageInProgress == StageInProgress.ANNONCE) {
                        System.out.println("GestionnaireElection:: Election bloqueé, on en recommence une");
                        startElection();
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    System.err.println("GestionnaireElection:: Problème dans la gestion du mutex");
                }
            }
        }

        return sites.get(siteElected);
    }
}
