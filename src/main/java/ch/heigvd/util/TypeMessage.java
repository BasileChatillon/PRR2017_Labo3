package ch.heigvd.util;

public enum TypeMessage {
    RESULTAT((byte) 0), // le message du résultat de l'éléction
    ANNONCE((byte) 1), // le message d'annonce de l'éléction
    QUITTANCE((byte) 2), // le message de quittance pour une annonce, un résultat.
    PING((byte) 3); // le message pour tester si l'élu n'est pas en panne.

    private byte valueMessage;

    TypeMessage(byte valueMessage) {
        this.valueMessage = valueMessage;
    }

    public byte getValueMessage() {
        return valueMessage;
    }
}
