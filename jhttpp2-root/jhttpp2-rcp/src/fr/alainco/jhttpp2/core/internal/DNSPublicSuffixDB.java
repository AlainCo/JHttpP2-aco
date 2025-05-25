package fr.alainco.jhttpp2.core.internal;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class DNSPublicSuffixDB {
	public static final String RESOURCE_PUBLIC_SUFFIX_LIST = "/public_suffix_list.dat";

	class PublicSuffixNode {
		public PublicSuffixNode() {

		}

		protected Map<String, PublicSuffixNode> subNodesByName = new HashMap<>();
		protected PublicSuffixNode defaultNode = null;
		protected Set<String> exceptionNode = new HashSet<>();
	}

	PublicSuffixNode rootNode = new PublicSuffixNode();

	public String getBaseSuffix(String dn) {
		StringBuilder sb = null;
		String[] names = dn.split("\\.");
		PublicSuffixNode currentNode = rootNode;
		for (int i = names.length - 1; i >= 0; i--) {
			String name = names[i];
			PublicSuffixNode newNode = currentNode.subNodesByName.get(name);
			boolean isLeaf=false;
			if (newNode == null || currentNode.exceptionNode.contains(name)) {
				isLeaf=true;
			} else if (currentNode.defaultNode != null) {
				newNode = currentNode.defaultNode;
			}
			currentNode = newNode;
			if (sb == null) {
				sb = new StringBuilder(dn.length());
			} else {
				sb.insert(0, ".");
			}
			sb.insert(0, name);
			if(isLeaf) break;
		}
		return sb.toString();
	}

	public void load(InputStream in) {
		InputStreamReader r0 = new InputStreamReader(in, StandardCharsets.UTF_8);
		BufferedReader r = new BufferedReader(r0, 10000);
		r.lines().forEach(line -> {
			String dn = line.trim();
			if (dn.startsWith("//") || dn.length() == 0)
				return;
			boolean isException = false;
			if (line.startsWith("!")) {
				isException = true;
				dn = dn.substring(1);
			}
			String[] names = dn.split("\\.");
			PublicSuffixNode currentNode = rootNode;
			for (int i = names.length - 1; i >= 0; i--) {
				String name = names[i];
				if (i == 0 && isException) {
					currentNode.exceptionNode.add(name);
				} else if ("*".equals(name)) {
					PublicSuffixNode newNode = new PublicSuffixNode();
					currentNode.defaultNode = newNode;
					currentNode = newNode;
				} else {
					PublicSuffixNode newNode = currentNode.subNodesByName.get(name);
					if (newNode == null) {
						newNode = new PublicSuffixNode();
						currentNode.subNodesByName.put(name, newNode);
					}
					currentNode = newNode;
				}
			}
		});
	}

	public static void main(String[] args) {
		InputStream in = DNSPublicSuffixDB.class.getResourceAsStream(RESOURCE_PUBLIC_SUFFIX_LIST);
		DNSPublicSuffixDB db = new DNSPublicSuffixDB();
		db.load(in);

		for (String dn : Arrays.asList("www.other.google.com", 
				"free.fr", "fr", "www.free.fr", 
				"truc.asso.fr", "asso.fr", "machin.truc.asso.fr",
				"bob.nom.br", "xxx.bob.nom.br", "nom.br",
				"bob.br","tom.bob.br","xxx.tom.bob.br",
				"truc.machin.ck", "bidule.truc.machin.ck", "machin.ck", "ck", 
				"www.ck", "truc.www.ck", "machin.truc.www.ck",
				"city.kawasaki.jp", "truc.city.kawasaki.jp", "machin.truc.city.kawasaki.jp",
				"truc.kawasaki.jp", "machin.truc.kawasaki.jp", "kawasaki.jp", "kawasaki.jp", "bob.machin.truc.kawasaki.jp")) {
			String base = db.getBaseSuffix(dn);
			System.out.printf("dn=%s prefix=%s%n", dn,base);

		}
	}
}
