package ch.heigvd.util;

/**
 * Un type énuméré qui représente les différents types de message utilisés dans le protocol de l'élection en annea.
 * Chaque message est représenté par un byte.
 */
public enum TypeMessage {
    RESULTAT((byte) 0), // le message du résultat de l'éléction
    ANNONCE((byte) 1), // le message d'annonce de l'éléction
    QUITTANCE((byte) 2), // le message de quittance pour une annonce, un résultat ou un ping.
    PING((byte) 3); // le message pour tester si l'élu n'est pas en panne.

    // La valeur du message
    private byte valueMessage;

    TypeMessage(byte valueMessage) {
        this.valueMessage = valueMessage;
    }

    public byte getValueMessage() {
        return valueMessage;
    }
}
