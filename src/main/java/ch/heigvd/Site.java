package ch.heigvd;

import java.net.InetAddress;

public class Site {
    private int numero;
    private int aptitude;
    private InetAddress ip;
    private int port;

    public Site(int numero, InetAddress ip, int port) {
        this.numero = numero;
        this.ip = ip;
        this.port = port;
        this.aptitude = calculateAptitude();
    }

    private int calculateAptitude(){
        return ip.getAddress()[3] + port;
    }

    public int getNumero() {
        return numero;
    }

    public void setNumero(int numero) {
        this.numero = numero;
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

    public String toString(){
        return "Site n°" + numero + " : " + ip.toString() + "\\" + port;
    }
}
