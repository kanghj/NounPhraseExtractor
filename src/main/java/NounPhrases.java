import opennlp.tools.cmdline.parser.ParserTool;
import opennlp.tools.parser.Parse;
import opennlp.tools.parser.ParserFactory;
import opennlp.tools.parser.ParserModel;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * Mostly code adapted from johnmiedema's https://gist.github.com/johnmiedema/e12e7359bcb17b03b8a0
 */
public class NounPhrases {

    private static class NounPhraseFilter {
        private final String regex;

        public NounPhraseFilter(String regex) {
            this.regex = regex;
        }

        public static NounPhraseFilter rejectNone() {
            return new NounPhraseFilter("[^.]*");
        }

        public static NounPhraseFilter rejectAll() {
            return new NounPhraseFilter("[.]*");
        }

        public static NounPhraseFilter rejectDetNoun() {
            return new NounPhraseFilter("dt nn([^p]|$).*");
        }

        public static NounPhraseFilter rejectSingleNounOrProper() {
            return new NounPhraseFilter("(nn|nnp)");
        }

        public static NounPhraseFilter rejectContainAnyProperNoun() {
            return new NounPhraseFilter(".*nnp.*");
        }

        public static NounPhraseFilter rejectConsecutive() {
            return new NounPhraseFilter("(nn|nnp){2,}");
        }

        public static NounPhraseFilter rejectLong() {
            return new NounPhraseFilter("(\\w{2,4} ){4,}.*");
        }

        public boolean isRejected(Parse possible) {
            return posAsString(possible).matches(
                    this.regex
            );
        }

        private String posAsString(Parse possible) {
            List<String> pos = new ArrayList<>();
            for (Parse tag : possible.getTagNodes()) {
                pos.add(tag.getType().toLowerCase());
            }
            return String.join(" ", pos);
        }
    }

    private final String sentence;
    private final List<NounPhraseFilter> filters;


    public NounPhrases(String sentence, List<NounPhraseFilter> filters) {
        this.sentence = sentence;
        this.filters = filters;
    }

    public NounPhrases(String sentence) {
        this(sentence,
             Arrays.asList(
                 NounPhraseFilter.rejectDetNoun(),
                 NounPhraseFilter.rejectSingleNounOrProper(),
                 NounPhraseFilter.rejectLong()
                 //NounPhraseFilter.rejectContainAnyProperNoun(),
                 //NounPhraseFilter.rejectConsecutive()
             )
        );
    }

    public Set<String> get() {
        Set<String> nounPhrases = new HashSet<>();
        try (
            InputStream saved = new FileInputStream("res/en-parser-chunking.bin")
            // http://opennlp.sourceforge.net/models-1.5/
        ) {
            Parse[] top = ParserTool.parseLine(
                sentence,
                ParserFactory.create(
                    new ParserModel(
                        saved
                    )
                ),
                1
            );

            for (Parse p : top) {
                nounPhrases.addAll(extractNounPhrases(p));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return nounPhrases;
    }


    private Set<String> extractNounPhrases(Parse p) {
        Set<String> nounPhrases = new HashSet<>();
        if (p.getType().equals("NP")) { // NP=noun phrase
            if (!isRejected(p)) {
                nounPhrases.add(p.toString());
            }
        }
        for (Parse child : p.getChildren()) {
            nounPhrases.addAll(extractNounPhrases(child));
        }
        return nounPhrases;
    }

    private boolean isRejected(Parse p) {
        for (NounPhraseFilter filter : this.filters) {
            if (filter.isRejected(p)) {
                return true;
            }
        }
        return false;
    }

    public static void main(String[] args) {
        System.out.println(
            new NounPhrases("Who is the author of The Call of the Wild ?").get()
        );
        // [The Call, the Wild]

        System.out.println(
            new NounPhrases("It is time to visit the doctor .").get()
        );

        // [It]
    }

}
