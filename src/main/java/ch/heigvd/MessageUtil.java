package ch.heigvd;

import java.util.ArrayList;
import java.util.List;

public class MessageUtil {

    public enum TypeMessage {
        ANNONCE((byte) 0), // le message d'annonce de l'éléction
        RESULTAT((byte) 1), // le message du résultat de l'éléction
        QUITTANCE((byte) 2), // le message de quittance pour une annonce, un résultat ou un ping
        PING((byte) 3); // le message pour tester si l'élu n'est pas en panne.

        private byte valueMessage;

        TypeMessage(byte valueMessage) {
            this.valueMessage = valueMessage;
        }
    }

    public static byte[] creationAnnonce(int numeroSite) {
        List<Integer> tmp = new ArrayList<Integer>();
        tmp.add(numeroSite);
        return creationAnnonce(tmp);
    }


    public static byte[] creationAnnonce(List<Integer> numeroSites) {

        byte[] message = new byte[1 + 4 * numeroSites.size()];

        message[0] = TypeMessage.ANNONCE.valueMessage;

        int j = 0;
        for (int numeroSite : numeroSites) {

            for (int i = 4; i >= 0; i--) {
                message[j + i + 1] = (byte) (numeroSite & 0xFF);
                numeroSite >>= 8;
            }

            j += 4;
        }

        return message;
    }

    public static byte[] creationResultat(int siteElu, int numeroSite) {
        List<Integer> tmp = new ArrayList<Integer>();
        tmp.add(numeroSite);
        return creationResultat(siteElu, tmp);
    }


    public static byte[] creationResultat(int siteElu, List<Integer> numeroSites) {

        byte[] message = new byte[1 + 4 + 4 * numeroSites.size()];

        message[0] = TypeMessage.RESULTAT.valueMessage;

        // Ajout du numéro de l'élu dans le message
        for (int i = 4; i >= 0; i--) {
            message[i + 1] = (byte) (siteElu & 0xFF);
            siteElu >>= 8;
        }


        // Ajout du numéro des sites  dans le message
        int j = 4;
        for (int numeroSite : numeroSites) {

            for (int i = 4; i >= 0; i--) {
                message[j * 4 + i + 1] = (byte) (numeroSite & 0xFF);
                numeroSite >>= 8;
            }

            j += 1;
        }

        return message;
    }

    public static List<Integer> extraitAnnonce(byte[] messageAnnonce) {

        List<Integer> resultat = new ArrayList<Integer>();

        int numberAnnonce = (messageAnnonce.length - 1) / 4;

        for (int j = 0; j < numberAnnonce; j++) {

            int numeroSite = 0;
            for (int i = 0; i < 4; i++) {
                numeroSite <<= 8;
                numeroSite |= (messageAnnonce[j * 4 + i + 1] & 0xFF);
            }

            resultat.add(numeroSite);
        }

        return resultat;
    }

    public static int extraitEluResultat(byte[] messageResultat) {

        int siteElu = 0;
        for (int i = 0; i < 4; i++) {
            siteElu <<= 8;
            siteElu |= (messageResultat[i + 1] & 0xFF);
        }

        return siteElu;
    }

    public static List<Integer> extraitNumeroSiteResultat(byte[] messageResultat) {

        List<Integer> resultat = new ArrayList<Integer>();

        int numberSite = (messageResultat.length - 5) / 4;

        for (int j = 1; j <= numberSite; j++) {

            int numeroSite = 0;
            for (int i = 0; i < 4; i++) {
                numeroSite <<= 8;
                numeroSite |= (messageResultat[j * 4 + i + 1] & 0xFF);
            }

            resultat.add(numeroSite);
        }

        return resultat;
    }

    public static byte[] creationQuittance() {
        byte[] message = new byte[1];
        message[0] = TypeMessage.QUITTANCE.valueMessage;

        return message;
    }

    public static byte[] creationPing() {
        byte[] message = new byte[1];
        message[0] = TypeMessage.PING.valueMessage;

        return message;
    }

    public static TypeMessage getTypeOfMessage(byte[] message) {
        return TypeMessage.values()[message[0]];
    }
}
