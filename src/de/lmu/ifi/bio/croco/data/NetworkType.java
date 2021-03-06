package de.lmu.ifi.bio.croco.data;

public enum NetworkType {
	ChIP("Chromatin Immunoprecipitation (ChIP)"), 
	OpenChrom("Open Chromatin (TFBS)"), 
	TextMining("Text-Mining"), 
	TFBS("Transcription Factor Binding Site (TFBS) prediction"),
	Database("Database"),
	Combined("Combined");
	
	public String niceName;
	
	NetworkType(String niceName){
	    this.niceName = niceName;
	}
}
