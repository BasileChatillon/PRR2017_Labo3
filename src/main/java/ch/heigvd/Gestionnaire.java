package ch.heigvd;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;

public class Gestionnaire extends Thread {
    private enum EtapeEnCours {
        ANNONCE,
        RESULTAT,
        FINI;
    }

    private EtapeEnCours etapeEnCours;
    private List<Site> sites;

    private int moi, voisin, siteUntel, coordinateur;
    private int monApt, aptUntel;
    private boolean attendreQuittance;

    private DatagramPacket paquet;
    private DatagramSocket socket;
    Object mutex;

    Gestionnaire(List<Site> sites, int moi) {
        this.sites = sites;
        this.moi = moi;
        this.voisin = (moi + 1) % sites.size();
        this.monApt = sites.get(moi).getAptitude();
        this.mutex = new Object();

        this.etapeEnCours = EtapeEnCours.FINI;

        this.attendreQuittance = false;
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

            DatagramPacket packet = new DatagramPacket(new byte[messageTailleMax], messageTailleMax);

            // attente de récéption d'un message
            try {
                socket.receive(packet);

                // Récupération du message
                byte[] message = new byte[packet.getLength()];
                System.arraycopy(packet.getData(), packet.getOffset(), message, 0, packet.getLength());

                switch (MessageUtil.getTypeOfMessage(message)) {
                    case ANNONCE:
                        System.out.println("Gestionnaire:: Reception d'un message d'annonce");
                        // on récupère les site qui ont émis une annonce
                        List<Integer> siteAnnonces = MessageUtil.extraitAnnonce(message);

                        if (siteAnnonces.contains(moi)) {
                            System.out.println("Gestionnaire:: Fin de la boucle, on détermine l'élu");
                            // On récupère le site qui à la meilleur atptitude
                            coordinateur = siteAnnonces.stream()
                                    .sorted(Comparator.comparing(u -> -sites.get((int) u).getAptitude())
                                            .thenComparing(u -> sites.get((int) u).getLastByteOfIp()))
                                    .limit(1)
                                    .collect(Collectors.toList())
                                    .get(0);

                            // On commence à envoyer les résultats
                            // On prépare le message
                            byte[] messageResultat = MessageUtil.creationResultat(coordinateur, moi);
                            System.out.println("Gestionnaire:: Election terminée, le site elu est " + coordinateur);
                            sendMessage(messageResultat);
                            // l'étape est maintenant les résultats
                            etapeEnCours = EtapeEnCours.RESULTAT;
                        } else {
                            // Si on est pas dans la liste, la phase de l'annonce est toujours en cours
                            // On se rajoute à la liste
                            siteAnnonces.add(moi);
                            // On crée le nouveau message et on l'envoie
                            byte[] messageAnnonce = MessageUtil.creationAnnonce(siteAnnonces);
                            System.out.println("Gestionnaire:: Election non terminée, on s'ajoute à la liste");
                            sendMessage(messageAnnonce);
                            etapeEnCours = EtapeEnCours.ANNONCE;
                        }
                        break;

                    case RESULTAT:
                        System.out.println("Gestionnaire:: Reception d'un message de résultat");
                        List<Integer> siteResultat = MessageUtil.extraitSitesResultat(message);
                        int elu = MessageUtil.extraitEluResultat(message);

                        if (siteResultat.contains(moi)) {
                            // Si le résultat à fait le tour de l'anneau, alors on est dans la liste et le coordinateur est le bon
                            etapeEnCours = EtapeEnCours.FINI;
                            synchronized (mutex){
                                mutex.notifyAll();
                            }
                        } else if (etapeEnCours == EtapeEnCours.RESULTAT && elu != coordinateur) {
                            byte[] messageAnnonce = MessageUtil.creationAnnonce(moi);
                            System.out.println("Gestionnaire:: On recommence une élection");
                            sendMessage(messageAnnonce);
                            etapeEnCours = etapeEnCours.ANNONCE;
                        } else if (etapeEnCours == EtapeEnCours.ANNONCE) {
                            coordinateur = elu;

                            siteResultat.add(moi);
                            byte[] messageResultat = MessageUtil.creationResultat(elu, siteResultat);
                            System.out.println("Gestionnaire:: Election terminée, annonce du résultat");
                            sendMessage(messageResultat);
                            // l'étape est maintenant les résultats
                            etapeEnCours = EtapeEnCours.RESULTAT;
                            synchronized (mutex){
                                mutex.notifyAll();
                            }
                        }
                        break;

                    case PING:
                        System.out.println("Gestionnaire:: Reception d'un message de ping");
                        byte[] messageResponse = MessageUtil.creationEcho();
                        System.out.println("Gestionnaire:: envoie de l'Echo");
                        sendMessage(message, packet.getAddress(), packet.getPort());
                        break;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void sendMessage(byte[] message) {
        sendMessage(message, sites.get(voisin).getIp(), sites.get(voisin).getPort());
    }

    private void sendMessage(byte[] message, InetAddress ip, int port) {
        paquet = new DatagramPacket(message, message.length, ip, port);
        try {
            socket.send(paquet);
        } catch (IOException e) {
            e.printStackTrace();
        }
        attendreQuittance = true;
    }

    public void commencerElection() {
        System.out.println("Gestionnaire:: début des élections");

        etapeEnCours = EtapeEnCours.ANNONCE;

        byte[] message = MessageUtil.creationAnnonce(moi);

        paquet = new DatagramPacket(message, message.length, sites.get(voisin).getIp(), sites.get(voisin).getPort());
        try {
            socket.send(paquet); // Envoyer le message // 8
        } catch (IOException e) {
            System.err.println("Gestionnaire:: Echec lors du démarrage de l'élection");
            e.printStackTrace();
        }

        attendreQuittance = true;
    }

    public Site getElu() {
        int elu = moi;

        if(etapeEnCours == EtapeEnCours.ANNONCE){
            synchronized (mutex) {
                try {
                    mutex.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    System.err.println("Gestionnaire:: Problème dans la gestion du mutex");
                }
            }
        }

        return sites.get(elu);
    }
}
