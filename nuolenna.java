/*
    nuolenna.java converts transliterated cuneiform text into cuneiform
    Copyright (C) 2018 Tommi Jauhiainen
	Copyright (C) 2026 University of Helsinki
 
    Claude and Mistral have been used as assistants in analyzing code and suggesting fixes and optimizations. 100% of code manually verified.


    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 
	If you use this program or the signlist in scientific work resulting tp
	publication, please use reference to the article they were first made
	for: https://aclanthology.org/W19-1409/
*/

import java.util.*;
import java.util.regex.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

class nuolenna {

	private static BufferedWriter writer = null;
	
	private static TreeMap<String,String> cuneiMap = new TreeMap<String,String>();
    private static boolean unifyNumbers = false;
    private static boolean old1 = false;
    private static boolean showUnknown = false;
    private static BufferedWriter unknownWriter = null;
    private static boolean cdli = false;
    private static final Pattern INDEKSI = Pattern.compile("(?<![~\\p{L}])(\\p{L}+)([0-9]+)");
    private static final Set<String> LISTANIMET = new HashSet<>(Arrays.asList("n", "m", "kwu", "lak", "zatu"));

    public static void main(String[] args) throws Exception {
        File classDir = new File(nuolenna.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        File file = new File(classDir, "sign_list.txt");
        
        loadindictionary(file);

        String inputPath = null;
        
        for (String arg : args) {
            if (arg.equals("-un")) {
                unifyNumbers = true;
            } else if (arg.equals("-old1")) {
                old1 = true;
            } else if (arg.equals("-unknown")) {
                showUnknown = true;
            } else if (arg.equals("-cdli")) {
                cdli = true;
            } else if (arg.startsWith("-")) {
                System.err.println("Unknown option: " + arg);
                System.exit(1);
            } else {
                inputPath = arg;
            }
        }
        
        if (inputPath == null) {
            System.err.println("Usage: java nuolenna [-un] [-old1] [-unknown] [-cdli] inputfile");
            System.exit(1);
        }
        
        File file2 = new File(inputPath);
        
        writer = new BufferedWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8));
        unknownWriter = new BufferedWriter(new OutputStreamWriter(System.err, StandardCharsets.UTF_8));
        
        try {
            if (cdli) {
                muutaCdli(file2);
            } else {
                muutanuoliksi(file2);
            }
        } finally {
            writer.flush();
            unknownWriter.flush();
        }
	}
	
	private static void muutanuoliksi(File file) {
		BufferedReader reader = null;
		try {
            reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8));
			
			String line = "";
			while ((line = reader.readLine()) != null) {
// Logograms are written in capitals, but the signs are the same
                line = line.toLowerCase(Locale.ROOT);
				
                String cuneiform = makeCuneiform(line);
                
                writer.write(cuneiform);
                writer.write("\n");
			}
		reader.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
    
    private static void muutaCdli(File file) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String id = null;
            StringBuilder teksti = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("&")) {
                    kirjoitaCdliTeksti(id, teksti);
                    id = line.substring(1).split("[ =]")[0];
                    teksti.setLength(0);
                } else if (line.matches("\\S+\\.\\s.*")) {
                    if (teksti.length() > 0) {
                        teksti.append(" å ");
                    }
                    teksti.append(line.replaceFirst("^\\S+\\.\\s+", ""));
                }
            }
            kirjoitaCdliTeksti(id, teksti);
        }
    }

    private static void kirjoitaCdliTeksti(String id, StringBuilder teksti) throws IOException {
        if (id != null) {
            writer.write(id + "\t" + makeCuneiform(muunnaCatf(teksti.toString().toLowerCase(Locale.ROOT))) + "\n");
        }
    }
    
    private static void reportUnknown(String tavu, String sana) throws IOException {
        if (showUnknown) {
            unknownWriter.write(tavu + "\t" + sana + "\n");
        }
    }
    
    private static String muunnaCatf(String s) {
        s = s.replace("sz", "š").replace("s,", "ṣ").replace("t,", "ṭ");
        s = s.replaceAll("(?<=\\p{L})x\\(", "ₓ(");
        // Index numbers after a sign value become subscripts (ban2 -> ban₂),
        // but numbered sign names such as N01 or KWU147 keep their digits
        Matcher m = INDEKSI.matcher(s);
        StringBuilder tulos = new StringBuilder();
        while (m.find()) {
            String kirjaimet = m.group(1);
            String numerot = m.group(2);
            if (LISTANIMET.contains(kirjaimet)) {
                m.appendReplacement(tulos, kirjaimet + numerot);
            } else {
                m.appendReplacement(tulos, kirjaimet + alaindeksi(numerot));
            }
        }
        m.appendTail(tulos);
        return tulos.toString();
    }

    private static String alaindeksi(String numerot) {
        StringBuilder sb = new StringBuilder();
        for (char c : numerot.toCharArray()) {
            sb.append((char) ('₀' + (c - '0')));
        }
        return sb.toString();
    }
    
    //private static String makeCuneiform(String transliteration) {
    private static String makeCuneiform(String transliteration) throws IOException {
        String cuneiform = "";
        String[] sanat = transliteration.split(" ");
        for (String sana : sanat) {
            //if (sana.matches("[0-9]+/[0-9]+\\(.*\\)") && cuneiMap.containsKey(sana)) {
            
            String alkuperainen = sana;
            
            // N-numerals may be written with subscript digits, e.g. 1(n₁₄)
            if (sana.matches("[0-9]+\\(n[₀-₉]+.*\\)")) {
                for (char c = '₀'; c <= '₉'; c++) {
                    sana = sana.replace(c, (char) ('0' + (c - '₀')));
                }
            }
            
            boolean fraction = sana.matches("[0-9]+/[0-9]+\\(.*\\)");
            boolean number = !unifyNumbers && sana.matches("[0-9]+\\(.*\\)");
            
            if ((fraction || number) && cuneiMap.containsKey(sana)) {
                cuneiform = cuneiform + cuneiMap.get(sana);
                continue;
            }
            
// REPETITION '(' GRAPHEME ')'
            if (sana.matches("^[1-90][1-90]*\\(.*\\)$")) {
                String merkki = sana.replaceAll("^[1-90][1-90]*\\(", "");
                merkki = merkki.replaceAll("\\)$", "");
                int maara = Integer.valueOf(sana.replaceAll("\\(.*$", ""));
                sana = merkki;
                if (maara > 10) {
                    maara = 10;
                }
                while (maara > 1) {
                    sana = sana + " " + merkki;
                    maara = maara - 1;
                }
            }
                                
// $-sign means that the reading is uncertain (the sign is still certain) so we just remove all dollar signs
            sana = sana.replaceAll("[\\$]", "");
// some complicated combination characters have their own sign in UTF, transformations here before removing pipes
            sana = sana.replaceAll("gad\\&gad\\.gar\\&gar", "kinda");
            sana = sana.replaceAll("bu\\&bu\\.ab", "sirsir");
            sana = sana.replaceAll("tur\\&tur\\.za\\&za", "zizna");
            sana = sana.replaceAll("še\\&še\\.tab\\&tab.gar\\&gar", "garadin₃");
// "Signs which have the special subscript ₓ must be qualified in ATF by placing the sign name in parentheses immediately after the sign value"
// http://oracc.museum.upenn.edu/doc/help/editinginatf/primer/inlinetutorial/index.html
            if (sana.matches(".*[\\.-][^\\.-]*ₓ\\(.*\\).*")) {
                while (sana.matches(".*[\\.-][^\\.-]*ₓ\\(.*\\).*")) {
                    sana = sana.replaceAll("(.*[\\.-])([^\\.-]*ₓ\\()([^\\)]*)(\\))(.*)", "$1$3$5");
                }
            }
            if (sana.matches(".*ₓ\\(.*\\).*")) {
                while (sana.matches(".*ₓ\\(.*\\).*")) {
                    sana = sana.replaceAll("(.*ₓ\\()([^\\)]*)(\\))(.*)", "$2$4");
                }
            }
            
            
// old or more precise readings can be within parenthesis straight after the sign. We just remove the parenthesis and what is inside them
// first we handle "xxx(|...|)"
            if (sana.matches(".*[^\\|\\&]\\(\\|[^\\|]*\\|\\).*")) {
                while (sana.matches(".*[^\\|\\&]\\(\\|[^\\|]*\\|\\).*")) {
                    sana = sana.replaceAll("(.*[^\\|\\&])(\\(\\|[^\\|]*\\|\\))(.*)", "$1$3");
                }
            }
                                
// then we handle "|...|(...)"
            if (sana.matches(".*\\|[^\\|]*\\|\\(.*\\).*")) {
                while (sana.matches(".*\\|[^\\|]*\\|\\(.*\\).*")) {
                    sana = sana.replaceAll("(.*\\|[^\\|]*\\|)(\\(.*\\))(.*)", "$1$3");
                }
            }
                                
// then we remove the more general case
            if (sana.matches(".*[\\.-][^\\.-]*[^\\|\\&]\\(.*\\).*")) {
                while (sana.matches(".*[\\.-][^\\.-]*[^\\|\\&]\\(.*\\).*")) {
                    sana = sana.replaceAll("(.*[\\.-][^\\.-]*[^\\|\\&])(\\(.*\\))(.*)", "$1$3");
                }
            }
            
            if (sana.matches(".*[^\\|\\&]\\(.*\\).*")) {
                while (sana.matches(".*[^\\|\\&]\\([^\\(\\)]*\\).*")) {
                    sana = sana.replaceAll("(.*[^\\|\\&])(\\([^\\(\\)]*\\))(.*)","$1$3");
                }
            }
            
            

// combination characters are inside pipes, but they are indicated also by combining markers, so we check markers and remove pipes
            sana = sana.replaceAll("\\|", "");
// Logograms separated internally by dots (e.g., GIR₂.TAB). If they are inside (...) they are not removed yet.
            if (!sana.matches(".*\\(.*\\..*\\).*")) {
                sana = sana.replaceAll("[.]", " ");
            }
// "Phonetic complements are preceded by a + inside curly brackets (e.g., KUR{+ud} = ikšud)."
// http://oracc.museum.upenn.edu/doc/help/editinginatf/primer/inlinetutorial/index.html
            sana = sana.replaceAll("\\{\\+", " ");
// joining characters are combined by + sign, we separate joining chars by replacing with whitespace
            sana = sana.replaceAll("\\+", " ");
            sana = sana.replaceAll("[-{}]", " ");
// LAGAŠ = ŠIR.BUR.LA
            sana = sana.replaceAll("lagaš ", "šir bur la ");

            sana = sana.replaceAll("  *", " ");

            String[] tavut = sana.split(" ");
            for (String tavu : tavut) {
                if (tavu.isEmpty()) {
                    continue;
                }
                if (tavu.equals("x")) {
                    cuneiform = cuneiform + ("xx");
                }
                else if (tavu.equals("å")) {
                    cuneiform = cuneiform + ("åå");
                }
                else {
// After the characters @ and ~ there is some annotation which should no affect cuneifying, so we just remove it.
                    if (tavu.matches(".*@[19cghknrstvz]")) {
                        tavu = tavu.replaceAll("@.*", "");
                    }
                    if (tavu.matches(".*~[abcdefptyv][1234dgpt]?p?")) {
                        tavu = tavu.replaceAll("~.*", "");
                    }
// All numbers to one
                    if (unifyNumbers && (tavu.matches("n[1-90][1-90]*") || tavu.matches("[1-90][1-90]*"))) {
                        tavu = "n01";
                    }

                    tavu = tavu.replaceAll("[\\(\\)]", "");
                     
                    if (cuneiMap.containsKey(tavu)) {
                        cuneiform = cuneiform + cuneiMap.get(tavu);
                    }
                    else if ((tavu.contains("×") || tavu.contains(".")) && !tavu.contains("&")) {
                        tavu = tavu.replaceAll("[\\.]", "×");
                        String[] alatavut = tavu.split("×");
                        for (String alatavu: alatavut) {
                            if (cuneiMap.containsKey(alatavu)) {
                                cuneiform = cuneiform + cuneiMap.get(alatavu);
                            //} else if (!old1 && !alatavu.isEmpty()) {
                            } else if (!alatavu.isEmpty()) {
                                reportUnknown(alatavu, alkuperainen);
                                if (!old1) {
                                    cuneiform = cuneiform + "_";
                                }
                            }
                        }
                    }
                    else if (tavu.equals("€") || tavu.equals("o")) {
                        cuneiform = cuneiform + "  ";
                    } else {
                        reportUnknown(tavu, alkuperainen);
                        if (!old1) {
                            cuneiform = cuneiform + "_";
                        }
                    }
//                    } else if (!old1) {
//                        cuneiform = cuneiform + "_";
//                    }
//                    else {
//                            System.out.print(tavu);
//                    }
                }
                
            }
        }
        if (!old1) {
            cuneiform = cuneiform.replaceAll("[^\\x{12000}-\\x{1268F}]+", "_");
        }
        return cuneiform;
    }
	
	private static void loadindictionary(File file) {
		
		BufferedReader reader = null;
		try {
            reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8));
			
			String line = "";
			
			while ((line = reader.readLine()) != null) {
				String translitteraatio = line.replaceAll("\t.*", "");
                translitteraatio = translitteraatio.toLowerCase(Locale.ROOT);
				String nuolenpaa = line.replaceAll(".*\t", "");
// We'll change all combination signs to just signs following each other
				nuolenpaa = nuolenpaa.replaceAll("x", "");
				nuolenpaa = nuolenpaa.replaceAll("X", "");
				nuolenpaa = nuolenpaa.replaceAll("\\.", "");
// we add to cuneimap only if there is a transliteration
				if (translitteraatio.length() > 0) {
					cuneiMap.put(translitteraatio, nuolenpaa);
				}
			}
			reader.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
