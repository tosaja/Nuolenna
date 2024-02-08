/*
    nuolenna.java converts transliterated cuneiform text into cuneiform
    Copyright (C) 2018 Tommi Jauhiainen
	Copyright (C) 2024 University of Helsinki

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
import java.io.*;

class nuolenna {

	private static BufferedWriter writer = null;
	
	private static TreeMap<String,String> cuneiMap = new TreeMap<String,String>();

	public static void main(String[] args) {
		
		File file = new File("sign_list.txt");
		
		loadindictionary(file);

		File file2 = new File(args[0]);
		
		muutanuoliksi(file2);
	}
	
	private static void muutanuoliksi(File file) {
		BufferedReader reader = null;
		try {
			reader = new BufferedReader(new FileReader(file));
			
			String line = "";
			
			while ((line = reader.readLine()) != null) {
// Logograms are written in capitals, but the signs are the same
				line = line.toLowerCase();
				
				String[] sanat = line.split(" ");
				for (String sana : sanat) {
// REPETITION '(' GRAPHEME ')'
					if (sana.matches("^[1-90][1-90]*\\(.*\\)$")) {
						String merkki = sana.replaceAll("^[1-90][1-90]*\\(", "");
						merkki = merkki.replaceAll("\\)$", "");
						int maara = Integer.valueOf(sana.replaceAll("\\(.*$", ""));
						sana = merkki;
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
						
// After the characters @ and ~ there is some annotation which should no affect cuneifying, so we just remove it.
						if (tavu.matches(".*@[19cghknrstvz]")) {
							tavu = tavu.replaceAll("@.*", "");
						}
						if (tavu.matches(".*~[abcdefptyv][1234dgpt]?p?")) {
							tavu = tavu.replaceAll("~.*", "");
						}
// All numbers to one
						if (tavu.matches("n[1-90][1-90]*") || tavu.matches("[1-90][1-90]*")) {
							tavu = "n01";
						}
						
						if (tavu.equals("1/2(iku)") || tavu.equals("1/4(iku)")) {
							tavu = "";
						}
						
						tavu = tavu.replaceAll("[\\(\\)]", "");

						if (cuneiMap.containsKey(tavu)) {
							System.out.print(cuneiMap.get(tavu));
						}
						else if ((tavu.contains("×") || tavu.contains(".")) && !tavu.contains("&")) {
							tavu = tavu.replaceAll("[\\.]", "×");
							String[] alatavut = tavu.split("×");
							for (String alatavu: alatavut) {
								if (cuneiMap.containsKey(alatavu)) {
									System.out.print(cuneiMap.get(alatavu));
								}
							}
						}
						else if (tavu.equals("€") || tavu.equals("o")) {
							System.out.print("  ");
						}
						else {
//							System.out.print(tavu);
						}
					}
				}
				System.out.print("\n");
			}
		reader.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private static void loadindictionary(File file) {
		
		BufferedReader reader = null;
		try {
			reader = new BufferedReader(new FileReader(file));
			
			String line = "";
			
			while ((line = reader.readLine()) != null) {
				String translitteraatio = line.replaceAll("\t.*", "");
				translitteraatio = translitteraatio.toLowerCase();
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
