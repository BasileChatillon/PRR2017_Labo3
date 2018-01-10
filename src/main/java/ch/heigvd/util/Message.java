package ch.heigvd.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Classe qui regroupe et implémente les diffréntes méthodes utiles à la création de message ou à la lecture des
 * messages échangés dans la procdéure.
 */
public class Message {
    /**
     * Permet de créer un message d'Annonce à partir d'un numéro de site ainsi que son aptitude
     *
     * @param siteNumber Le numéro du site
     * @return Un tableau de byte représentant le message
     */
    public static byte[] createAnnonce(int siteNumber, int siteAptitude) {
        // On crée un buffer de la bonne taille
        byte[] message = new byte[1];

        // Ajout du type de message au début du buffer
        message[0] = TypeMessage.ANNONCE.getValueMessage();

        return createAnnonce(message, siteNumber, siteAptitude);
    }

    /**
     * Permet de créer un message d'Annonce à partir d'un numéro de site et d'un message d'annonce recu.
     * Il suffit de juste ajouter à la fin du message notre numéro de site ainsi que son aptitude
     *
     * @param oldMessage   Le vieux message d'annonce à réutiliser
     * @param siteNumber   Le numéro du site
     * @param siteAptitude l'aptitude du site
     * @return Un tableau de byte représentant le message
     */
    public static byte[] createAnnonce(byte[] oldMessage, int siteNumber, int siteAptitude) {

        int oldMessageLength = oldMessage.length;
        byte[] newMessage = new byte[oldMessageLength + 8]; // le 8 bient de 4 byte pour le nombre du site et 4 pour l'aptitude

        // Copie du contenu du vieux message dans le nouveau
        System.arraycopy(oldMessage, 0, newMessage, 0, oldMessageLength);

        // On utilise un bit shift pour pouvoir récupérer les 4 bytes de l'int et les insérer un à un dans le buffer
        for (int i = 3; i > 0; i--) {
            newMessage[oldMessageLength + i] = (byte) (siteNumber & 0xFF);
            siteNumber >>= 8;
        }

        for (int i = 3; i > 0; i--) {
            newMessage[oldMessageLength + 4 + i] = (byte) (siteAptitude & 0xFF); // On ajoute 4 de décalage
            siteAptitude >>= 8;
        }

        return newMessage;
    }

    /**
     * Permet de créer un message de résultat (selon le protocole). Il contiendra en premier le site élu puis le site
     * ayant vu le message
     *
     * @param siteElu    Le site elu par l'élection
     * @param numeroSite Le site qui crée le message de résultat
     * @return Un tableau de byte représentant le message
     */
    public static byte[] createResultat(int siteElu, int numeroSite) {
        // On crée un buffer de la bonne taille
        byte[] message = new byte[5];

        message[0] = TypeMessage.RESULTAT.getValueMessage();

        // Ajout du numéro de l'élu dans le message grâce à un bit shift
        for (int i = 3; i > 0; i--) {
            message[i + 1] = (byte) (siteElu & 0xFF);
            siteElu >>= 8;
        }

        return createResultat(message, numeroSite);
    }

    /**
     * Permet de créer un message de Résultat à partir d'un numéro de site et d'un message de résultat reçu.
     * Il suffit de juste ajouter à la fin du message notre numéro de site
     *
     * @param oldMessage Le vieux message de résultat à réutiliser
     * @param siteNumber Le numéro du site
     * @return Un tableau de byte représentant le message
     */
    public static byte[] createResultat(byte[] oldMessage, int siteNumber) {

        int oldMessageLength = oldMessage.length;
        // On crée un buffer de la bonne taille
        byte[] newMessage = new byte[oldMessageLength + 4];

        System.arraycopy(oldMessage, 0, newMessage, 0, oldMessageLength);

        // Ajout du numéro de chaque site dans le message grâce à un bit shift
        for (int i = 3; i > 0; i--) {
            newMessage[oldMessageLength + i] = (byte) (siteNumber & 0xFF);
            siteNumber >>= 8;
        }

        return newMessage;
    }

    /**
     * Permet de récupérer depuis un mesage d'Annonce la map associant les numéros de site à leur amplitude
     *
     * @param messageAnnonce Le message dont on veut extraire les informations
     * @return Une map associant des numéros de site à leur amplitude
     */
    public static Map<Integer, Integer> extractAnnonce(byte[] messageAnnonce) {

        // Le tableau dans lequel on va stocker les sites.
        Map<Integer, Integer> result = new HashMap<>();
        int siteNumber;
        int amplitude;

        // Calcule du nombre de sites qui on vu l'annonce
        int numberAnnonce = (messageAnnonce.length - 1) / 8; // On retire le 1 qui est le type de message ensuite on a 2 int ce qui fait 2x4 = 8

        // Parcours du message d'annonce pour récupérer les numéros des différents sites ainsi que leur amplitude
        for (int j = 0; j < numberAnnonce; j++) {

            siteNumber = 0;
            for (int i = 0; i < 4; i++) {
                siteNumber <<= 8;
                siteNumber |= (messageAnnonce[j * 8 + i + 1] & 0xFF);
            }

            amplitude = 0;
            for (int i = 0; i < 4; i++) {
                amplitude <<= 8;
                amplitude |= (messageAnnonce[j * 8 + i + 5] & 0xFF);
            }

            result.put(siteNumber, amplitude);
        }

        return result;
    }

    /**
     * Permet de récupérer depuis un mesage de résultat le numéro du site élu lors de l'élection
     *
     * @param messageResultat Le message dont on veut extraire les informations
     * @return Le numéro du site élu
     */
    public static int extractElectedFromResult(byte[] messageResultat) {
        int siteElu = 0;
        // Parcours d'un bout du message pour récupérer le site élu
        for (int i = 0; i < 4; i++) {
            siteElu <<= 8;
            siteElu |= (messageResultat[i + 1] & 0xFF);
        }

        return siteElu;
    }

    /**
     * Permet de récupérer depuis un mesage de résultat la liste des numéros de tous les sites qui ont vu le résultat
     *
     * @param messageResult Le message dont on veut extraire les informations
     * @return La liste des numéros de site
     */
    public static List<Integer> extractSitesFromResult(byte[] messageResult) {

        List<Integer> result = new ArrayList<>();

        // Calcule du nombre de sites qui on vu le résultat
        int totalSite = (messageResult.length - 5) / 4;

        // Parcours du message d'annonce pour récupérer les numéros des différents sites
        for (int j = 1; j <= totalSite; j++) {

            int siteNumber = 0;
            for (int i = 0; i < 4; i++) {
                siteNumber <<= 8;
                siteNumber |= (messageResult[j * 4 + i + 1] & 0xFF);
            }

            result.add(siteNumber);
        }

        return result;
    }

    /**
     * Permet de créer un message de quittance
     *
     * @return Un tableau de byte représentant le message
     */
    public static byte[] createQuittance() {
        // On ajoute juste le type du message
        byte[] message = new byte[1];
        message[0] = TypeMessage.QUITTANCE.getValueMessage();

        return message;
    }

    /**
     * Crée un message de Ping
     *
     * @return Un tableau de byte représentant le message
     */
    public static byte[] createPing() {
        // On ajoute juste le type du message
        byte[] message = new byte[1];
        message[0] = TypeMessage.PING.getValueMessage();

        return message;
    }

    /**
     * Récupère le type du message
     *
     * @param message Le message dont on veut récupéré le type
     * @return Le type du message
     */
    public static TypeMessage getTypeOfMessage(byte[] message) {
        return TypeMessage.values()[message[0]];
    }
}
