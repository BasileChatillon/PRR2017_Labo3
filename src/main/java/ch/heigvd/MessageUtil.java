package ch.heigvd;

import java.util.ArrayList;
import java.util.List;


/**
 * Classe qui regroupe et implémente les diffréntes méthodes utiles à la création de message ou à la lecture des
 * messages échangés dans la procdéure.
 */
public class MessageUtil {

    /**
     * Un type énuméré qui représente les différents types de message utilisés dans le protocol de l'élection en anneau
     * Chaque message est représenté par un byte.
     */
    public enum TypeMessage {
        RESULTAT((byte) 0), // le message du résultat de l'éléction
        ANNONCE((byte) 1), // le message d'annonce de l'éléction
        QUITTANCE((byte) 2), // le message de quittance pour une annonce, un résultat.
        PING((byte) 3); // le message pour tester si l'élu n'est pas en panne.

        // La valeur du message
        private byte valueMessage;

        TypeMessage(byte valueMessage) {
            this.valueMessage = valueMessage;
        }
    }

    /**
     * Permet de créer un message d'Annonce à partir d'un numéro de site
     *
     * @param numeroSite Le numéro du site
     * @return Un tableau de byte représentant le message
     */
    public static byte[] creationAnnonce(int numeroSite) {
        List<Integer> tmp = new ArrayList<>();
        tmp.add(numeroSite);

        return creationAnnonce(tmp);
    }

    /**
     * Permet de créer un message d'annonce à partir d'une liste de numéro de site
     *
     * @param numeroSites Les numéros des sites
     * @return Un tableau de byte représentant le message
     */
    public static byte[] creationAnnonce(List<Integer> numeroSites) {

        // On crée un buffer de la bonne taille
        byte[] message = new byte[1 + 4 * numeroSites.size()];

        // Ajout du type de message au début du buffer
        message[0] = TypeMessage.ANNONCE.valueMessage;

        int j = 0;
        // On parcourt chaque numéro de site et on les ajoute dans le buffer
        for (int numeroSite : numeroSites) {

            // On utilise un bit shift pour pouvoir récupèrer les 4 bytes de l'int et les insérer un à un dans le buffer
            for (int i = 3; i > 0; i--) {
                message[j + i + 1] = (byte) (numeroSite & 0xFF);
                numeroSite >>= 8;
            }

            j += 4;
        }

        return message;
    }

    /**
     * Permet de créer un message de résultat (selon le protocole). Il contiendra en premier le site élu puis le site
     * ayant vu le message
     *
     * @param siteElu    Le site elu par l'élection
     * @param numeroSite Le site qui crée le message de résultat
     * @return Un tableau de byte représentant le message
     */
    public static byte[] creationResultat(int siteElu, int numeroSite) {
        List<Integer> tmp = new ArrayList<>();
        tmp.add(numeroSite);

        return creationResultat(siteElu, tmp);
    }

    /**
     * Permet de créer un message de résultat à partir d'un site élu ainsi qu'un liste de numéro de site
     *
     * @param siteElu     Le site elu par l'élection
     * @param numeroSites Les sites qui ont vu le message
     * @return Un tableau de byte représentant le message
     */
    public static byte[] creationResultat(int siteElu, List<Integer> numeroSites) {
        // On crée un buffer de la bonne taille
        byte[] message = new byte[1 + 4 + 4 * numeroSites.size()];

        message[0] = TypeMessage.RESULTAT.valueMessage;

        // Ajout du numéro de l'élu dans le message grâce à un bit shift
        for (int i = 3; i > 0; i--) {
            message[i + 1] = (byte) (siteElu & 0xFF);
            siteElu >>= 8;
        }

        // Ajout du numéro de chaque site dans le message grâce à un bit shift
        int j = 1;
        for (int numeroSite : numeroSites) {

            for (int i = 3; i > 0; i--) {
                message[j * 4 + i + 1] = (byte) (numeroSite & 0xFF);
                numeroSite >>= 8;
            }

            j += 1;
        }

        return message;
    }

    /**
     * Permet de récupérer depuis un mesage d'Annonce la liste des numéros de tous les sites qui on vu l'annonce
     *
     * @param messageAnnonce Le message dont on veut extraire les informations
     * @return La liste des numéros de site
     */
    public static List<Integer> extraitAnnonce(byte[] messageAnnonce) {

        // Le tableau dans lequel on va stocker les sites.
        List<Integer> result = new ArrayList<>();

        // Calcule du nombre de sites qui on vu l'annonce
        int numberAnnonce = (messageAnnonce.length - 1) / 4;

        // Parcours du message d'annonce pour récupérer les numéros des différents sites
        for (int j = 0; j < numberAnnonce; j++) {

            int numeroSite = 0;
            for (int i = 0; i < 4; i++) {
                numeroSite <<= 8;
                numeroSite |= (messageAnnonce[j * 4 + i + 1] & 0xFF);
            }

            result.add(numeroSite);
        }

        return result;
    }

    /**
     * Permet de récupérer depuis un mesage de résultat le numéro du site élu lors de l'élection
     *
     * @param messageResultat Le message dont on veut extraire les informations
     * @return Le numéro du site élu
     */
    public static int extraitEluResultat(byte[] messageResultat) {
        int siteElu = 0;
        // Parcours d'un bout du message pour récupérer le site élu
        for (int i = 0; i < 4; i++) {
            siteElu <<= 8;
            siteElu |= (messageResultat[i + 1] & 0xFF);
        }

        return siteElu;
    }

    /**
     * Permet de récupérer depuis un mesage de résultat la liste des numéros de tous les sites qui on vu le résultat
     *
     * @param messageResult Le message dont on veut extraire les informations
     * @return La liste des numéros de site
     */
    public static List<Integer> extraitSitesResultat(byte[] messageResult) {

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
    public static byte[] creationQuittance() {
        // On ajoute juste le type du message
        byte[] message = new byte[1];
        message[0] = TypeMessage.QUITTANCE.valueMessage;

        return message;
    }

    /**
     * Crée un message de Ping
     *
     * @return Un tableau de byte représentant le message
     */
    public static byte[] creationPing() {
        byte[] message = new byte[1];
        // On ajoute juste le type du message
        message[0] = TypeMessage.PING.valueMessage;

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
