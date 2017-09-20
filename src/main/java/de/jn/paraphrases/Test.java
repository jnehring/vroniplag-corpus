package de.jn.paraphrases;

import com.sun.org.apache.xerces.internal.impl.xpath.regex.Match;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.util.regex.Pattern;

/**
 * Created by jan on 12.09.17.
 */
public class Test {

    public static void main(String[] args){
        String html = "<td>abcd<br/>bla<hr>bla</td>";
        int index = html.toLowerCase().indexOf("<hr>");
        System.err.println(html);
        String trim = html.substring(0,index) + "</td>";
        System.err.println(trim);

        Document doc = Jsoup.parse(trim);
        System.err.println(doc.text());
    }
}
