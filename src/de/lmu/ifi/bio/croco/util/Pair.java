package de.lmu.ifi.bio.croco.util;

/**
 * An ordered pair of objects
 * @author pesch
 *
 * @param <FIRST>
 * @param <SECOND>
 */
public class Pair<FIRST,SECOND> implements Tuple<FIRST,SECOND>, Comparable<Pair<FIRST,SECOND>> {
	
	
	private static final long serialVersionUID = 1L;
	
	private FIRST first;
	private SECOND second;
	
	public Pair(FIRST first, SECOND second){
		
		this.first = first;
		this.second  = second;
	}
	
	
	public void setFirst(FIRST first) {
		this.first = first;
	}

	public void setSecond(SECOND second) {
		this.second = second;
	}

	@Override
	public String toString(){
	
		return first.toString() + "-" + second.toString();
	}
   @Override
    public int hashCode() {
    	int hashCode = 1;
	    hashCode = (first==null ? 0 : first.hashCode());
	    hashCode += (second==null ? 0 : second.hashCode());
    	return hashCode;
    }
	
	@Override
	public boolean equals(Object o){
		if ( o instanceof Pair<?,?>){
			Pair<FIRST,SECOND> toCheck = (Pair<FIRST, SECOND>) o;
			
			if ( (toCheck.getFirst().equals(this.getFirst()) && toCheck.getSecond().equals(this.getSecond()))
				 ||
				 (toCheck.getSecond().equals(this.getFirst()) && toCheck.getFirst().equals(this.getSecond())) ){
				return true;
			}
			else{
				return false;
			}	
		}else{
			return false;
		}
	}


	public FIRST getFirst() {
		return first;
	}


	public SECOND getSecond() {
		return second;
	}


	@Override
	public int compareTo(Pair<FIRST, SECOND> o) {
		throw new UnsupportedOperationException();
	}

    public static<FIRST,SECOND> Pair<FIRST,SECOND> create(FIRST first, SECOND second) {
            return new Pair<FIRST,SECOND> (first,second);
    }

	
}
