/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package readsocialstask;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.GetMethod;


/**
 *
 * @author Jacques
 */
public class SocialsRetriever {
    public void Execute() {

        try{
            HttpClient client = new HttpClient();
            GetMethod req = new GetMethod("http://www.google.it");
            int statusCode = client.executeMethod(req);
            if (statusCode != HttpStatus.SC_OK) {
                System.err.println("Method failed: " + req.getStatusLine());
            }
            String resp = req.getResponseBodyAsString();
            System.out.println(resp);
        } catch (Exception e){
            System.out.println(e);
            
        }
    }
}
