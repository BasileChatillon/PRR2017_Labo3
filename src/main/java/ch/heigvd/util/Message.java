package ch.heigvd.util;

import java.util.ArrayList;
import java.util.List;

public class Message {

    public static byte[] createAnnonce(int siteNumber) {
        List<Integer> tmp = new ArrayList<Integer>();
        tmp.add(siteNumber);
        return createAnnonce(tmp);
    }

    public static byte[] createAnnonce(List<Integer> siteNumbers) {

        byte[] message = new byte[1 + 4 * siteNumbers.size()];
        message[0] = TypeMessage.ANNONCE.getValueMessage();

        int j = 0;
        for (int siteNumber : siteNumbers) {

            for (int i = 3; i > 0; i--) {
                message[j + i + 1] = (byte) (siteNumber & 0xFF);
                siteNumber >>= 8;
            }

            j += 4;
        }

        return message;
    }

    public static byte[] createResult(int siteElected, int siteNumber) {
        List<Integer> tmp = new ArrayList<>();
        tmp.add(siteNumber);

        return createResult(siteElected, tmp);
    }

    public static byte[] createResult(int siteElected, List<Integer> siteNumbers) {

        byte[] message = new byte[1 + 4 + 4 * siteNumbers.size()];
        message[0] = TypeMessage.RESULTAT.getValueMessage();

        // Ajout du numéro de l'élu dans le message
        for (int i = 3; i > 0; i--) {
            message[i + 1] = (byte) (siteElected & 0xFF);
            siteElected >>= 8;
        }


        // Ajout du numéro des sites  dans le message
        int j = 1;
        for (int siteNumber : siteNumbers) {
            for (int i = 3; i > 0; i--) {
                message[j * 4 + i + 1] = (byte) (siteNumber & 0xFF);
                siteNumber >>= 8;
            }

            j += 1;
        }

        return message;
    }

    public static List<Integer> extractAnnonce(byte[] messageAnnonce) {

        List<Integer> result = new ArrayList<>();
        int numberOfAnnonce = (messageAnnonce.length - 1) / 4;

        for (int j = 0; j < numberOfAnnonce; j++) {

            int siteNumber = 0;
            for (int i = 0; i < 4; i++) {
                siteNumber <<= 8;
                siteNumber |= (messageAnnonce[j * 4 + i + 1] & 0xFF);
            }

            result.add(siteNumber);
        }

        return result;
    }

    public static int extractElectedFromResult(byte[] messageResultat) {

        int siteElected = 0;
        for (int i = 0; i < 4; i++) {
            siteElected <<= 8;
            siteElected |= (messageResultat[i + 1] & 0xFF);
        }

        return siteElected;
    }

    public static List<Integer> extractSitesFromResult(byte[] messageResultat) {

        List<Integer> sites = new ArrayList<Integer>();
        int numberOfSite = (messageResultat.length - 5) / 4;

        for (int j = 1; j <= numberOfSite; j++) {

            int siteNumber = 0;
            for (int i = 0; i < 4; i++) {
                siteNumber <<= 8;
                siteNumber |= (messageResultat[j * 4 + i + 1] & 0xFF);
            }

            sites.add(siteNumber);
        }

        return sites;
    }

    public static byte[] createQuittance() {
        byte[] message = new byte[1];
        message[0] = TypeMessage.QUITTANCE.getValueMessage();

        return message;
    }

    public static byte[] createPing() {
        byte[] message = new byte[1];
        message[0] = TypeMessage.PING.getValueMessage();

        return message;
    }

    public static TypeMessage getTypeOfMessage(byte[] message) {
        return TypeMessage.values()[message[0]];
    }
}
