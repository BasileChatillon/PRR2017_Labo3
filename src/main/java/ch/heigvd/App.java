package ch.heigvd;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Hello world!
 *
 */
public class App extends Thread
{

    public App(String[] args) {

        List<Site> sites = getAllSite();

        System.out.println("App: affichage des sites");
        for(Site site : sites){
            System.out.println(site);
        }
    }

    private List<Site> getAllSite(){
        List<Site> sites = new ArrayList<Site>();

        InputStream inputStream;

        try {
            Properties prop = new Properties();
            String propFileName = "site.properties";
            ClassLoader classLoader = getClass().getClassLoader();

            inputStream = classLoader.getResourceAsStream(propFileName);

            if (inputStream != null) {
                prop.load(inputStream);
            } else {
                throw new FileNotFoundException("property file '" + propFileName + "' not found in the classpath");
            }

            // get the property value and print it out
            String number_site = prop.getProperty("number_site");
            System.out.println(number_site);

            String siteAddress;
            InetAddress siteIP;
            int sitePort;
            for(int i = 0 ; i < Integer.parseInt(number_site) ; ++i){
                siteAddress = prop.getProperty(String.valueOf(i));
                String[] values = siteAddress.split(":");
                siteIP = InetAddress.getByName(values[0]);
                sitePort = Integer.parseInt(values[1]);
                sites.add(new Site(i, siteIP, sitePort));
            }

            inputStream.close();

        } catch (Exception e) {
            System.out.println("Exception: " + e);
        }

        return sites;
    }

    public static void main( String[] args )
    {
        new App(args).start();
    }


}
