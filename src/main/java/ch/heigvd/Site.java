package ch.heigvd;

import java.net.InetAddress;

/**
 * La classe Site représente toutes le informations utiles pour pouvoir localiser un site. (soit en tout cas son IP
 * et son numéro de port).
 * Elle contient également un numéro qui permet de l'identifier.
 * De plus, nous avons décidé de stocker son aptitude. En effet, celle-ci ne devant pas changer, nous avons trouvé plus
 * efficace de la calculer une seule fois et de simplement la récupérer quand on en a besoin.
 */
public class Site {
    private int number; // Le numéro du site
    private int aptitude; // L'aptitude du site
    private InetAddress ip; // L'addresse IP du site
    private int port; // Le port du site

    // Constructeur
    public Site(int number, InetAddress ip, int port) {
        this.number = number;
        this.ip = ip;
        this.port = port;
        this.aptitude = calculateAptitude();
    }

    /**
     * Function permettant de caculer l'aptitude d'un site selon la consigne.
     * @return un int étnat l'aptitude du site.
     */
    private int calculateAptitude(){
        return ip.getAddress()[3] + port;
    }

    /**
     * Permet de récupérer le dernier byte de l'addresse IP du site. (uitle pour l'aptitude)
     * @return Le 3ème byte de l'IP
     */
    public byte getLastByteOfIp() {
        return ip.getAddress()[3];
    }

    /*** Getter Setter ***/
    public int getNumber() {
        return number;
    }

    public void setNumber(int number) {
        this.number = number;
    }

    public int getAptitude() {
        return aptitude;
    }

    public void setAptitude(int aptitude) {
        this.aptitude = aptitude;
    }

    public InetAddress getIp() {
        return ip;
    }

    public void setIp(InetAddress ip) {
        this.ip = ip;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    @Override
    public String toString(){
        return "Site n°" + number + " : " + ip.toString() + " - port n°" + port + " (aptitude : " + aptitude + ")";
    }
}
