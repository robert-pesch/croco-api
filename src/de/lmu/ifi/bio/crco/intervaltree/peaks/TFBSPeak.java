package de.lmu.ifi.bio.crco.intervaltree.peaks;

import java.util.ArrayList;
import java.util.List;

import de.lmu.ifi.bio.crco.data.Entity;
import de.lmu.ifi.bio.crco.data.genome.Transcript;
import de.lmu.ifi.bio.crco.util.StringUtil;

public class TFBSPeak extends Peak {
	
	private List<Entity> factors;
	private Transcript target;
	private String motifId;
	//Distance from TFBS to annotated TSS (strand corrected; <0 Upstream; >0 Downstream)
	private Integer distanceToTranscript = null;
	private Float pValue;
	
	public float getpValue() {
		return pValue;
	}

	public List<Entity> getFactors(){
		return factors;
	}
	public Transcript getTarget(){
		return target;
	}

	public TFBSPeak(String chrom,Entity factor,String motifId,Transcript target,Integer distanceToTranscript, Float pValue, Float score, int start, int end) {
		super(chrom,start, end,score);
		this.target = target;
		this.factors = new ArrayList<Entity>();
		this.factors.add(factor);
		this.pValue = pValue;
		this.motifId = motifId;
		this.distanceToTranscript = distanceToTranscript;
	}
	public TFBSPeak(String chrom,List<Entity> factors,String motifId,Transcript target,Integer distanceToTranscript, Float pValue, Float score, int start, int end) {
		super(chrom,start, end,score);
		this.target = target;
		this.factors = factors;
		this.pValue = pValue;
		this.motifId = motifId;
		this.distanceToTranscript = distanceToTranscript;
	}
	
	public String getMotifId() {
		return motifId;
	}

	public Integer getDistanceToTranscript() {
		return distanceToTranscript;
	}

	public TFBSPeak(int start, int end){
		super(start,end);
	}
	



}
