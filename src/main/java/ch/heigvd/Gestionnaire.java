package ch.heigvd;

import java.io.IOException;
import java.net.*;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Gestionnaire extends Thread {
    /**
     * Un enum qui premet de définir l'étape en cours
     */
    private enum StageInProgress {
        ANNONCE,
        RESULTAT,
        FINI;
    }

    private StageInProgress stageInProgress; // l'étape en cours
    private List<Site> sites; // Une Liste contenant tous les sites existant dans l'anneau

    private int numberSiteTot; // le nombre de site dans l'anneau

    private int myAptitude; // La valeur de mon aptitude
    private int me, // Notre numéro de site
            neighbor, // le site se trouvant après nous, soit celui à qui ont doit envoyer les messages
            siteElected; // Le site qui sera élu

    private DatagramPacket paquet;
    private DatagramSocket socketReception; // Le socket qui va permettre la récéption des messages venant des autres sites
    private Object mutex; // le mutex qui permet d'éviter que l'applicatif accède à l'élu lorsqu'il n'est pas encore élu

    Gestionnaire(List<Site> sites, int me) {
        this.sites = sites;
        this.me = me;
        this.neighbor = (me + 1) % sites.size();
        this.myAptitude = sites.get(me).getAptitude();
        this.mutex = new Object();
        this.numberSiteTot = sites.size();

        this.stageInProgress = StageInProgress.FINI;

        try {
            System.out.println("Gestionnaire:: Création du socketReception de récéption pour le site n°" + me);
            socketReception = new DatagramSocket(sites.get(me).getPort());
        } catch (SocketException e) {
            System.err.println("Gestionnaire:: Echeck de la création du socker pour le site n°" + me);
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        while (true) {
            // la taille du tampon est le plus long message possible, soit quand tous les sites on répondu à l'annonce.
            int sizeMessageMax = 1 + 2 * 4 * numberSiteTot;

            DatagramPacket packetRecu = new DatagramPacket(new byte[sizeMessageMax], sizeMessageMax);

            // attente de récéption d'un message
            try {
                socketReception.receive(packetRecu);

                // Récupération du message
                byte[] message = new byte[packetRecu.getLength()];
                System.arraycopy(packetRecu.getData(), packetRecu.getOffset(), message, 0, packetRecu.getLength());

                switch (MessageUtil.getTypeOfMessage(message)) {
                    case ANNONCE:
                        // On commence par envoyer la quittance à l'émetteur du message
                        sendQuittance(packetRecu.getAddress(), packetRecu.getPort());

                        System.out.println("Gestionnaire:: Reception d'un message d'annonce");
                        // on récupère les site qui ont émis une annonce
                        Map<Integer, Integer> siteAnnonces = MessageUtil.extraitAnnonce(message);

                        if (siteAnnonces.containsKey(me)) {
                            System.out.println("Gestionnaire:: Fin de la boucle, on détermine l'élu");
                            // On récupère le site qui à la meilleur atptitude et ensuite par rapport à l'IP
                            siteElected = siteAnnonces.entrySet().stream()
                                    .sorted(Map.Entry.<Integer, Integer>comparingByValue().reversed()
                                            .thenComparing(u -> sites.get(u.getKey()).getLastByteOfIp()))
                                    .limit(1)
                                    .map(u -> u.getKey())
                                    .collect(Collectors.toList())
                                    .get(0);

                            // On commence à envoyer les résultats
                            // On prépare le message
                            byte[] messageResultat = MessageUtil.creationResultat(siteElected, me);
                            System.out.println("Gestionnaire:: Election terminée, le site elu est " + siteElected);
                            sendMessage(messageResultat);

                            // l'étape est maintenant les résultats
                            stageInProgress = StageInProgress.RESULTAT;
                        } else {
                            // Si on est pas dans la liste, la phase de l'annonce est toujours en cours
                            // On récupère le vieux message d'annonce et on en reconstruit un à partir de celui-ci
                            byte[] messageAnnonce = MessageUtil.creationAnnonce(message, me, myAptitude);
                            System.out.println("Gestionnaire:: Election non terminée, on s'ajoute à la liste");

                            sendMessage(messageAnnonce);

                            stageInProgress = StageInProgress.ANNONCE;
                        }
                        break;

                    case RESULTAT:
                        // On commence par envoyer la quittance à l'émetteur du message
                        sendQuittance(packetRecu.getAddress(), packetRecu.getPort());

                        System.out.println("Gestionnaire:: Reception d'un message de résultat");
                        List<Integer> siteResultat = MessageUtil.extraitSitesResultat(message);
                        int elu = MessageUtil.extraitEluResultat(message);

                        if (siteResultat.contains(me)) {
                            // Si le résultat à fait le tour de l'anneau, alors on est dans la liste et le siteElected est le bon
                            stageInProgress = StageInProgress.FINI;
                            synchronized (mutex) {
                                mutex.notifyAll();
                            }
                        } else if (stageInProgress == StageInProgress.RESULTAT && elu != siteElected) {
                            // Dans le cas ou l'on reçoit un message de résultat après en avoir eu un autre résultat
                            // Il y a une erreur alors on relance une élection
                            byte[] messageAnnonce = MessageUtil.creationAnnonce(me, myAptitude);
                            System.out.println("Gestionnaire:: On recommence une élection");
                            sendMessage(messageAnnonce);
                            stageInProgress = stageInProgress.ANNONCE;
                        } else if (stageInProgress == StageInProgress.ANNONCE) {
                            // Dans le cas il y a une annonce et qu'on recoit un message de résultat
                            siteElected = elu; // On élit le site

                            // On crée un message de résultat en prenant l'ancien et en s'ajoutant dedans.
                            byte[] messageResultat = MessageUtil.creationResultat(message, me);
                            System.out.println("Gestionnaire:: Election terminée, annonce du résultat");
                            sendMessage(messageResultat);

                            // l'étape est maintenant les résultats
                            stageInProgress = StageInProgress.RESULTAT;
                            synchronized (mutex) {
                                mutex.notifyAll();
                            }
                        }
                        break;

                    case PING:
                        System.out.println("Gestionnaire:: Reception d'un message de ping");
                        sendQuittance(packetRecu.getAddress(), packetRecu.getPort());
                        break;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Permet d'envoyer un message de quittance
     *
     * @param ip   L'adresse du site de destination
     * @param port Le port du site de destination
     */
    private void sendQuittance(InetAddress ip, int port) {
        byte[] message = MessageUtil.creationQuittance();
        DatagramPacket packetQuittance = new DatagramPacket(message, message.length, ip, port);
        try {
            socketReception.send(packetQuittance);
        } catch (IOException e) {
            System.err.println("Gestionnaire:: échec d'envoi de la quittance");
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
            boolean tourAnneau = false;

            // Tant qu'on a pas recu la réponse et qu'on a pas fait un tour de l'anneau, on va contacter le voisin suivant
            while (quittanceReceived == false && tourAnneau != true) {
                // On commence par récupérer les infos du site à contacter
                ip = sites.get(workingNeighbor).getIp();
                port = sites.get(workingNeighbor).getPort();

                try {
                    // On crée le packet et on l'envoie au site
                    packetMessage = new DatagramPacket(message, message.length, ip, port);
                    socketQuittance.send(packetMessage);

                    // On met le timeOut à 200 millisecondes et on attend la réponse du site
                    socketQuittance.setSoTimeout(200);
                    socketQuittance.receive(packetMessage);

                    // Si on a recu un message dans le temps imparti, on vérifie quand même que c'est le bon message
                    if (MessageUtil.getTypeOfMessage(packetMessage.getData()) == MessageUtil.TypeMessage.QUITTANCE) {
                        quittanceReceived = true;
                    }
                } catch (SocketTimeoutException e) {
                    // Si il y a eu un timeout, ça veut dire que le site voisin n'est pas disponible, donc qu'on va demander
                    // au voisin d'après
                    System.out.println("Gestionnaire:: neighbor n°" + workingNeighbor + " est inactif");
                    workingNeighbor = (workingNeighbor + 1) % numberSiteTot;

                    // Dans le cas ou on a fait un trour, on finit les élections
                    if (workingNeighbor == me) {
                        stageInProgress = StageInProgress.FINI;
                        tourAnneau = true;
                        siteElected = me;
                        // on libère le mutex pour que l'applifactif puisse récupérer les informations
                        synchronized (mutex) {
                            mutex.notifyAll();
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } catch (SocketException e) {
            System.err.println("Gestionnaire:: problème lors de l'ouverture du socketReception d'envoie du message");
            e.printStackTrace();
        }
    }


    /**
     * Permet de commencer les éléctions en envoyant un message d'annonce
     */
    public void statElection() {
        System.out.println("Gestionnaire:: début des élections");
        stageInProgress = StageInProgress.ANNONCE;
        byte[] message = MessageUtil.creationAnnonce(me, myAptitude);

        sendMessage(message);
    }

    /**
     * Permet de récupérer le site élu.
     * Cette fonction est blocante tant qu'une éléection est en cours.
     *
     * @return le site élu
     */
    public Site getElu() {
        // On vérifie qu'on est pas en train de faire une annonce avant de récupérér les infos du site
        if (stageInProgress == StageInProgress.ANNONCE) {
            synchronized (mutex) {
                try {
                    mutex.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    System.err.println("Gestionnaire:: Problème dans la gestion du mutex");
                }
            }
        }

        return sites.get(siteElected);
    }
}
