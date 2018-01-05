package ch.heigvd;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class Gestionnaire extends Thread {
    private List<Site> sites;

    private int moi, voisin, siteUntel, coordinateur;
    private int monApt, aptUntel;
    private boolean attendreQuittance,
            annonceEncours;

    private DatagramPacket paquet;
    private DatagramSocket socket;

    Gestionnaire(List<Site> sites, int moi) {
        this.moi = moi;
        this.voisin = (moi + 1) % sites.size();
        this.monApt = sites.get(moi).getAptitude();

        this.annonceEncours = false;
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
                        List<Integer> siteAnnonces = MessageUtil.extraitAnnonce(message);
                        if (siteAnnonces.contains(moi)) {

                            coordinateur = siteAnnonces.stream()
                                    .map(u ->sites.get(u))
                                    .sorted(Comparator.comparing(Site::getAptitude)
                                    .thenComparing(Site::getLastByteOfIp))
                                    .limit(1)
                                    .map(u -> u.getNumero())
                                    .collect(Collectors.toList()).get(0);

                            coordinateur = 1;

                            byte[] messageResultat = MessageUtil.creationResultat(coordinateur, moi);
                            sendMessage(messageResultat);
                            annonceEncours = false;
                        } else {
                            siteAnnonces.add(moi);
                            byte[] messageAnnonce = MessageUtil.creationAnnonce(siteAnnonces);
                            sendMessage(messageAnnonce);
                            annonceEncours = true;
                        }

                        break;

                    case RESULTAT:
                        break;

                    case PING:
                        break;

                    case QUITTANCE:
                        break;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void sendMessage(byte[] message) {
        paquet = new DatagramPacket(message, message.length, sites.get(voisin).getIp(), sites.get(voisin).getPort());
        try {
            socket.send(paquet);
        } catch (IOException e) {
            e.printStackTrace();
        }
        attendreQuittance = true;
    }

    public void commencerElection() {
        System.out.println("début des élections");
        annonceEncours = true;

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
