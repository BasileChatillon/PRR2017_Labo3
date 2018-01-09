package ch.heigvd;

import ch.heigvd.util.Message;
import ch.heigvd.util.TypeMessage;
import org.omg.CORBA.TIMEOUT;

import java.io.IOException;
import java.net.*;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class Gestionnaire extends Thread {
    private enum EtapeEnCours {
        ANNONCE,
        RESULTAT,
        FINI;
    }

    private EtapeEnCours etapeEnCours;
    private List<Site> sites;

    private int numberSiteTot;

    private int moi, voisin, coordinateur;
    private int monApt;

    private final int TIMEOUT_QUITTANCE;

    private DatagramPacket paquet;
    private DatagramSocket socket;
    Object mutex;

    Gestionnaire(List<Site> sites, int moi, int TIMEOUT_QUITTANCE) {
        this.sites = sites;
        this.moi = moi;
        this.voisin = (moi + 1) % sites.size();
        this.monApt = sites.get(moi).getAptitude();
        this.mutex = new Object();
        this.numberSiteTot = sites.size();
        this.TIMEOUT_QUITTANCE = TIMEOUT_QUITTANCE;

        this.etapeEnCours = EtapeEnCours.FINI;

        try {
            System.out.println("Gestionnaire:: Création du socket de récéption pour le site n°" + moi);
            socket = new DatagramSocket(sites.get(moi).getPort());
        } catch (SocketException e) {
            System.err.println("Gestionnaire:: Echeck de la création du socker pour le site n°" + moi);
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        while (true) {
            // la taille du tampon est le plus long message possible, soit quand tous les sites on répondu à l'annonce.
            int messageTailleMax = 1 + 2 * 4 * sites.size();

            DatagramPacket packetRecu = new DatagramPacket(new byte[messageTailleMax], messageTailleMax);

            // attente de récéption d'un message
            try {
                socket.receive(packetRecu);

                // Récupération du message
                byte[] message = new byte[packetRecu.getLength()];
                System.arraycopy(packetRecu.getData(), packetRecu.getOffset(), message, 0, packetRecu.getLength());

                switch (Message.getTypeOfMessage(message)) {
                    case ANNONCE:
                        // On commence par envoyer la quittance à l'émetteur du message
                        sendQuittance(packetRecu.getAddress(), packetRecu.getPort());

                        System.out.println("Gestionnaire:: Reception d'un message d'annonce");
                        // on récupère les site qui ont émis une annonce
                        List<Integer> siteAnnonces = Message.extractAnnonce(message);

                        if (siteAnnonces.contains(moi)) {
                            System.out.println("Gestionnaire:: Fin de la boucle, on détermine l'élu");
                            // On récupère le site qui à la meilleure aptitude
                            coordinateur = siteAnnonces.stream()
                                    .sorted(Comparator.comparing(u -> -sites.get((int) u).getAptitude())
                                            .thenComparing(u -> sites.get((int) u).getLastByteOfIp()))
                                    .limit(1)
                                    .collect(Collectors.toList())
                                    .get(0);

                            // On commence à envoyer les résultats
                            // On prépare le message
                            byte[] messageResultat = Message.createResult(coordinateur, moi);
                            System.out.println("Gestionnaire:: Election terminée, le site elu est " + coordinateur);
                            sendMessage(messageResultat);
                            // l'étape est maintenant les résultats
                            etapeEnCours = EtapeEnCours.RESULTAT;
                        } else {
                            // Si on est pas dans la liste, la phase de l'annonce est toujours en cours
                            // On se rajoute à la liste
                            siteAnnonces.add(moi);
                            // On crée le nouveau message et on l'envoie
                            byte[] messageAnnonce = Message.createAnnonce(siteAnnonces);
                            System.out.println("Gestionnaire:: Election non terminée, on s'ajoute à la liste");
                            sendMessage(messageAnnonce);
                            etapeEnCours = EtapeEnCours.ANNONCE;
                        }
                        break;

                    case RESULTAT:
                        // On commence par envoyer la quittance à l'émetteur du message
                        sendQuittance(packetRecu.getAddress(), packetRecu.getPort());

                        System.out.println("Gestionnaire:: Reception d'un message de résultat");
                        List<Integer> siteResultat = Message.extractSitesFromResult(message);
                        int elu = Message.extractElectedFromResult(message);

                        if (siteResultat.contains(moi)) {
                            // Si le résultat à fait le tour de l'anneau, alors on est dans la liste et le coordinateur est le bon
                            etapeEnCours = EtapeEnCours.FINI;
                            synchronized (mutex) {
                                mutex.notifyAll();
                            }
                        } else if (etapeEnCours == EtapeEnCours.RESULTAT && elu != coordinateur) {
                            byte[] messageAnnonce = Message.createAnnonce(moi);
                            System.out.println("Gestionnaire:: On recommence une élection");
                            sendMessage(messageAnnonce);
                            etapeEnCours = etapeEnCours.ANNONCE;
                        } else if (etapeEnCours == EtapeEnCours.ANNONCE) {
                            coordinateur = elu;

                            siteResultat.add(moi);
                            byte[] messageResultat = Message.createResult(elu, siteResultat);
                            System.out.println("Gestionnaire:: Election terminée, annonce du résultat");
                            sendMessage(messageResultat);
                            // l'étape est maintenant les résultats
                            etapeEnCours = EtapeEnCours.RESULTAT;
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

    private void sendQuittance(InetAddress ip, int port) {
        byte[] message = Message.createQuittance();
        paquet = new DatagramPacket(message, message.length, ip, port);
        try {
            socket.send(paquet);
        } catch (IOException e) {
            System.err.println("Gestionnaire:: échec d'envoi de la quittance");
            e.printStackTrace();

        }
    }

    private void sendMessage(byte[] message) {
        InetAddress ip;
        int port;
        int voisinFonctionnel = voisin;

        DatagramSocket socketQuittance = null;
        DatagramPacket packetMessage = null;
        try {
            socketQuittance = new DatagramSocket();
        } catch (SocketException e) {
            System.err.println("Gestionnaire:: problème lors de l'ouverture du socket d'envoie du message");
            e.printStackTrace();
        }

        boolean quittanceRecue = false;
        boolean tourAnneau = false;


        while (quittanceRecue == false && tourAnneau != true) {
            ip = sites.get(voisinFonctionnel).getIp();
            port = sites.get(voisinFonctionnel).getPort();

            try {
                packetMessage = new DatagramPacket(message, message.length, ip, port);
                socketQuittance.send(packetMessage);

                socketQuittance.setSoTimeout(TIMEOUT_QUITTANCE);
                socketQuittance.receive(packetMessage);

                if (Message.getTypeOfMessage(packetMessage.getData()) == TypeMessage.QUITTANCE) {
                    quittanceRecue = true;
                }
            } catch (SocketTimeoutException e) {
                System.out.println("Gestionnaire:: voisin n°" + voisinFonctionnel + " est inactif");
                voisinFonctionnel = (voisinFonctionnel + 1) % numberSiteTot;
                if (voisinFonctionnel == moi) {
                    etapeEnCours = EtapeEnCours.FINI;
                    tourAnneau = true;
                    coordinateur = moi;
                    synchronized (mutex) {
                        mutex.notifyAll();
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void commencerElection() {
        System.out.println("Gestionnaire:: début des élections");
        etapeEnCours = EtapeEnCours.ANNONCE;
        byte[] message = Message.createAnnonce(moi);

        sendMessage(message);
    }

    public Site getElu() {
        if (etapeEnCours == EtapeEnCours.ANNONCE) {
            synchronized (mutex) {
                try {
                    mutex.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    System.err.println("Gestionnaire:: Problème dans la gestion du mutex");
                }
            }
        }

        return sites.get(coordinateur);
    }
}
