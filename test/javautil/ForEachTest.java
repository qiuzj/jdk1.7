package javautil;
/**
  * Java Class to show how for-each loop works in Java
  */
public class ForEachTest {  
 
    public static void main(String args[]){
        CustomCollection<String> myCollection = new CustomCollection<String>();
        myCollection.add("Java");
        myCollection.add("Scala");
        myCollection.add("Groovy");
 
        //What does this code will do, print language, throw exception or compile time error
        for(String language : myCollection){ // Can only iterate over an array or an instance of java.lang.Iterable
            System.out.println(language);
        }
    }
}