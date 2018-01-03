package de.jn.paraphrases;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by jan on 12.09.17.
 */
public class Test {

    public static void main(String[] args){
//        String html = "<td>abcd<br/>bla<hr>bla</td>";
//        int index = html.toLowerCase().indexOf("<hr>");
//        System.err.println(html);
//        String trim = html.substring(0,index) + "</td>";
//        System.err.println(trim);
//
//        Document doc = Jsoup.parse(trim);
//        System.err.println(doc.text());
    	
    	
    	List<String> p = Arrays.asList("a", "b", "c", "d", "e");
    	List<String> s = Arrays.asList("a", "c", "e", "g");
    	
    	List<String> unavailable = p.stream()
                .filter(e -> s.contains(e))
                .collect(Collectors.toList());
    	
    	System.out.println(unavailable);
    	
    }
}
