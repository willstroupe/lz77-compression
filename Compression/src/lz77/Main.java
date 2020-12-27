package lz77;


import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedList;
import java.util.Scanner;

public class Main {
	public static final int MODULUS = 531;
	public static final int BASE_SIZE = 256;
	public static final int MATCH_LENGTH = 4;
	public static final int MAX_SIZE = 255; //keep below 256, stored in single byte
	public static final int WRITE_BUFFER_SIZE = 65536;
	public static final int MAX_DIST = 32767;
	public static int loc;
	public static void main(String[] args) throws IOException {
		ArrayList<Integer>[] hashTable = new ArrayList[MODULUS];
		for (int i = 0; i < MODULUS; i++) {
			hashTable[i] = new ArrayList<Integer>();
		}
		
		Scanner sysIn = new Scanner(System.in);
		System.out.println("Compress or decompress (C/D)");
		String utilType = sysIn.nextLine();
		while (!utilType.equals("C") && !utilType.equals("D")) {
			System.out.println("Input must be \"C\" or \"D\"");
			utilType = sysIn.nextLine();
		}
		if (utilType.equals("C")) {
			System.out.println("Enter path to UTF-8 encoded text file");
			String filePath = sysIn.nextLine();
			while (true) {
				try {
					byte[] inData = Files.readAllBytes(Paths.get(filePath));
					compress(hashTable, inData);
					break;
				}
				catch (Exception e) {
					System.out.println("File not found, please reenter");
					filePath = sysIn.nextLine();
				}
			}
		}
		else if (utilType.equals("D")) {
			System.out.println("Enter path for file to decode");
			String filePath = sysIn.nextLine();
			while (true) {
				try {
					byte[] data = Files.readAllBytes(Paths.get(filePath));
					decompress(data);
					break;
				}
				catch (Exception e) {
					System.out.println("File not found, please reenter");
					filePath = sysIn.nextLine();
				}
			}
		}
	}
	public static void decompress(byte[] str) throws IOException {
		int distFromEnd = 0;
		String inStr = "BUFFEREND";
		byte[] testStr = inStr.getBytes();
		File outputFile = new File("C:/JavaUtils/output.txt");
		int i = 0;
		//for retrieving match data -- always the MAX_DIST bytes before byteBuffer
		byte[] searchBuffer = new byte[MAX_DIST];
		byte[] byteBuffer = new byte[MAX_DIST];
		int bufferLoc = 0;
		
		OutputStream outputWriter = new FileOutputStream(outputFile);
		while (i < str.length) {
			if (str[i] == -1) { //decode a code block
				int	matchDist = ((str[i+1] << 8) & 0xff00) + (str[i+2] & 0x00ff);
				int matchLength = str[i+3];
				System.out.println(i);
				if (matchLength < 0) matchLength += 256;
				//find the string this {dist, length} pair refers to
				if (bufferLoc + matchLength >= byteBuffer.length) {
					distFromEnd = byteBuffer.length - bufferLoc;
					outputWriter.write(byteBuffer, 0, bufferLoc);
					outputWriter.write(testStr);
					outputWriter.write(testStr);
					searchBuffer = byteBuffer;
					byteBuffer = new byte[MAX_DIST];
					bufferLoc = 0;
				}
				if (matchDist <= bufferLoc) { //string contained in byteBuffer
					for (int j = 0; j < matchLength; j++) {
						byteBuffer[bufferLoc] = byteBuffer[bufferLoc - matchDist];
						bufferLoc++;
					}
					//string contained in searchBuffer
				} else if (matchDist - matchLength > bufferLoc) { 
					for (int j = 0; j < matchLength; j++) {
						byteBuffer[bufferLoc] = searchBuffer[searchBuffer.length
						                                     - distFromEnd 
						                                     + bufferLoc 
						                                     - matchDist];
						bufferLoc++;
					}
				}
				else { //string contained in both searchBuffer and byteBuffer
					int startLoc = bufferLoc;
					//add substring at the end of searchBuffer
					for (int j = 0; j < matchDist - startLoc; j++) {
						byteBuffer[bufferLoc] = searchBuffer[searchBuffer.length 
						                                     - distFromEnd 
						                                     + bufferLoc 
						                                     - matchDist];
						bufferLoc++;
					}
					//add substring at the beginning of byteBuffer
					for (int j = 0; j < matchLength - (matchDist - startLoc); j++) {
						byteBuffer[bufferLoc] = byteBuffer[j];
						bufferLoc++;
					}
				}
				i += 4;
			} else { //copy a literal
				if (bufferLoc >= byteBuffer.length) {
					distFromEnd = 0;
					outputWriter.write(byteBuffer);
					outputWriter.write(testStr);
					searchBuffer = byteBuffer;
					byteBuffer = new byte[MAX_DIST];
					bufferLoc = 0;
				}
				byteBuffer[bufferLoc] = str[i];
				bufferLoc++;
				i++;
			}
		outputWriter.write(byteBuffer, 0, bufferLoc);
		outputWriter.close();
		}
	}
	
	
	public static int stringHasher(byte[] strIn) {
		int hash = 0;
		for(int i = 0; i < strIn.length - 1; i++) {
			hash = BASE_SIZE*(hash + strIn[i]);
			hash = hash % MODULUS;
		}
		hash += strIn[strIn.length - 1];
		hash %= MODULUS;
		return hash;
	}
	
	public static int rollStringHasher(byte firstByte, byte nextByte, int hash) {
		hash += MODULUS;
		hash -= (firstByte*Math.pow(BASE_SIZE, MATCH_LENGTH - 1)) % MODULUS;
		hash = (hash*BASE_SIZE + nextByte) % MODULUS;
		return hash;
	}
	
	public static void compress(ArrayList<Integer>[] hashTable, byte[] str) 
	throws IOException {
		//initialize the filewriter
		File outputFile = new File("C:/JavaUtils/output.lz77");
		OutputStream outputWriter = new FileOutputStream(outputFile);
		
		//check if str is shorter than MATCH_LENGTH
		if (str.length < MATCH_LENGTH) {
			outputWriter.write(str);
			outputWriter.close();
			return;
		}
		
		//location in str
		int loc = 0;
		
		//initialize the byte buffer
		byte[] byteBuffer = new byte[WRITE_BUFFER_SIZE];
		int bufferLoc = 0;
		
		//add the first string to the hash table and buffer
		Deque<Byte> inStr = new LinkedList<Byte>();
		byte[] firstStr = new byte[MATCH_LENGTH];
		byteBuffer[0] = str[0];
		bufferLoc++;
		for (int i = 0; i < MATCH_LENGTH; i++) {
			firstStr[i] = str[i];
			inStr.add(str[i]);
		}
		int hash = stringHasher(firstStr);
		hashTable[hash].add(loc);
		loc++;
		int newLoc;
		
		//test stuff
		int matchTimes = 0;
		
		//main compression loop
		while (loc + MATCH_LENGTH < str.length) {
			byte testByte = inStr.pop();
			hash = rollStringHasher(testByte, str[loc + MATCH_LENGTH], hash);
			System.out.println("hash: " + hash);
			inStr.add(str[loc + MATCH_LENGTH]);
			
			//check where string hash is in hashTable and find longest match
			short matchDist = -1;
			//to ensure that matches are always more than MATCH_LENGTH long
			int bestMatchLength = MATCH_LENGTH;
			
			if (!hashTable[hash].isEmpty()) {
				for (int i = hashTable[hash].size() - 1; i >= 0; i--) {
					int tempMatchLength = 0;
					
					//check that hash match isn't too far away
					if (loc - hashTable[hash].get(i) < MAX_DIST) {
						
						//check how many characters match
						while (true) {
							//check that str[loc+tempMatchLength] exists
							if (loc + tempMatchLength >= str.length) break;
							
							if (str[loc + tempMatchLength] != 
								str[hashTable[hash].get(i) + tempMatchLength]
								|| tempMatchLength >= MAX_SIZE) {
								break;
							}
							tempMatchLength++;
						}
					}
					if (tempMatchLength > bestMatchLength) {
						bestMatchLength = tempMatchLength;
						matchDist = (short)(loc - hashTable[hash].get(i));
					}
				}
				System.out.println(matchDist);
			}
			
			//if substring is found previously, 
			//add [dist, length] pair to compressed string
			//denote distance section by byte value -1
			//distance is always 2 bytes long, length 1 byte long
			//else, add literal to string
			if (matchDist >= 0) {
				matchTimes++;
				 //-1 for arr start loc, 
				 //-4 for indicator byte and 3 dist, length bytes
				if (bufferLoc > byteBuffer.length - 5) {
					//write buffer to file and reinitialize buffer
					outputWriter.write(byteBuffer, 0, bufferLoc);
					// outputWriter.write(testStr);
					bufferLoc = 0;
					byteBuffer = new byte[WRITE_BUFFER_SIZE];
				}
				byteBuffer[bufferLoc] = (byte)-1;
				//big-endian
				byteBuffer[bufferLoc+1] = (byte)((matchDist >> 8) & 0xff);
				byteBuffer[bufferLoc+2] = (byte)(matchDist & 0xff);
				byteBuffer[bufferLoc+3] = (byte)bestMatchLength;
				bufferLoc += 4;
				newLoc = loc + bestMatchLength;
			} else {
				if (bufferLoc > byteBuffer.length - 1 ) {
					outputWriter.write(byteBuffer, 0, bufferLoc);
					bufferLoc = 0;
					byteBuffer = new byte[WRITE_BUFFER_SIZE];
				}
				byteBuffer[bufferLoc] = str[loc];
				bufferLoc++;
				newLoc = loc + 1;
			}
			
			//add hash of next string(s) to hashTable and update inStr
			hashTable[hash].add(loc);
			loc++;
			for (; loc < newLoc; loc++) {
				if (loc + MATCH_LENGTH >= str.length) break;
				hash = rollStringHasher(inStr.pop(), str[loc + MATCH_LENGTH], hash);
				hashTable[hash].add(loc);
				inStr.add(str[loc + MATCH_LENGTH]); 
			}
		}
		outputWriter.write(byteBuffer, 0, bufferLoc);
		outputWriter.close();
	}
}
