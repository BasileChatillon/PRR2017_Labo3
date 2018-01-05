package ch.heigvd;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
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

    private int moi, voisin, siteUntel, coordinateur;
    private int monApt, aptUntel;
    private boolean attendreQuittance;

    private DatagramPacket paquet;
    private DatagramSocket socket;

    Gestionnaire(List<Site> sites, int moi) {
        this.moi = moi;
        this.voisin = (moi + 1) % sites.size();
        this.monApt = sites.get(moi).getAptitude();

        this.etapeEnCours = EtapeEnCours.FINI;

        this.attendreQuittance = false;
        try {
            System.out.println("création du socker pour le site n°" + moi);
            socket = new DatagramSocket(sites.get(moi).getPort());
        } catch (SocketException e) {
            System.err.println("Echeck de la création du socker pour le site n°" + moi);
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        while (true) {
            // la taille du tampon est le plus long message possible, soit quand tous les sites on répondu à l'annonce.
            byte[] message = new byte[1 + 2 * 4 * sites.size()];

            DatagramPacket packet = new DatagramPacket(message, message.length);

            // attente de récéption d'un message
            try {
                socket.receive(packet);

                switch (MessageUtil.getTypeOfMessage(message)) {
                    case ANNONCE:
                        // on récupère les site qui ont émis une annonce
                        List<Integer> siteAnnonces = MessageUtil.extraitAnnonce(message);

                        if (siteAnnonces.contains(moi)) {

                            // On récupère le site qui à la meilleur atptitude
                            coordinateur = siteAnnonces.stream()
                                    .sorted(Comparator.comparing(u -> sites.get((int) u).getAptitude())
                                            .thenComparing(u -> -sites.get((int) u).getLastByteOfIp()))
                                    .limit(1)
                                    .collect(Collectors.toList())
                                    .get(0);

                            // On commence à envoyer les résultats
                            // On prépare le message
                            byte[] messageResultat = MessageUtil.creationResultat(coordinateur, moi);
                            sendMessage(messageResultat);
                            // l'étape est maintenant les résultats
                            etapeEnCours = EtapeEnCours.RESULTAT;
                        } else {
                            // Si on est pas dans la liste, la phase de l'annonce est toujours en cours
                            // On se rajoute à la liste
                            siteAnnonces.add(moi);
                            // On crée le nouveau message et on l'envoie
                            byte[] messageAnnonce = MessageUtil.creationAnnonce(siteAnnonces);
                            sendMessage(messageAnnonce);
                            etapeEnCours = EtapeEnCours.ANNONCE;
                        }

                        break;

                    case RESULTAT:
                        List<Integer> siteResultat = MessageUtil.extraitSitesResultat(message);
                        int elu = MessageUtil.extraitEluResultat(message);

                        if (siteResultat.contains(moi)) {
                            // Si le résultat à fait le tour de l'anneau, alors on est dans la liste et le coordinateur est le bon
                            etapeEnCours = EtapeEnCours.FINI;
                        } else if (etapeEnCours == EtapeEnCours.RESULTAT && elu != coordinateur) {
                            byte[] messageAnnonce = MessageUtil.creationAnnonce(moi);
                            sendMessage(messageAnnonce);
                            etapeEnCours = etapeEnCours.ANNONCE;
                        } else if (etapeEnCours == EtapeEnCours.ANNONCE) {
                            coordinateur = elu;
                            siteResultat.add(moi);
                            byte[] messageResultat = MessageUtil.creationResultat(elu, siteResultat);
                            sendMessage(messageResultat);
                            // l'étape est maintenant les résultats
                            etapeEnCours = EtapeEnCours.RESULTAT;
                        }
                        break;

                    case PING:
                        byte[] messageResponse = MessageUtil.creationEcho();
                        sendMessage(message, packet.getAddress(), packet.getPort());
                        break;

                    case QUITTANCE:

                        attendreQuittance = false;
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
        System.out.println("début des élections");
        etapeEnCours = EtapeEnCours.ANNONCE;

        byte[] message = MessageUtil.creationAnnonce(moi);

        paquet = new DatagramPacket(message, message.length, sites.get(voisin).getIp(), sites.get(voisin).getPort());
        try {
            socket.send(paquet); // Envoyer le message // 8
        } catch (IOException e) {
            System.err.println("Echec lors du démarrage de l'élection");
            e.printStackTrace();
        }

        attendreQuittance = true;
    }

    public int getElu() {
        return coordinateur;
    }
}
