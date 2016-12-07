import java.util.*;

// one list will be used per measure

public class NoteList{

    private ArrayList<Note> noteList;
    
    public NoteList(){
        this.noteList = new ArrayList<Note>();
    }
    
    public NoteList(ArrayList<Note> noteList){
        this.noteList = noteList;
    }
    
    public ArrayList<Note> getList(){
        return this.noteList;
    }
    
    public void setList(ArrayList<Note> l){
        this.noteList = l;
    }
    
    public void addNote(Note n){
        this.noteList.add(n);
    }
    
    public String[] compileNotes(int size){
        // LCM for mixed intervals: 48
        // 0-6 = lanes 1-7
        // 7 = SC lane
        
        String[] lanes = new String[size];
        ArrayList<Note> tempList;
        int trueInterval = 16;
        int tokenDiv = 4;
        
        for (int i = 0; i < lanes.length; i++)
        	lanes[i] = "";
        
        for (int i = 0; i < size;i++){
            tempList = new ArrayList<Note>();
            trueInterval = 16;
            tokenDiv = 4;
            
            for (Note n : noteList){
               if (i == (n.getLane()))
                    tempList.add(n);
            }
            
            if (tempList.size() == 0){
                lanes[i] = null;
                continue;
            }
            
            for (Note n : tempList){
                if (n.getInterval() == 24){
                    trueInterval = 24;
                }
                
                if (n.getInterval() != trueInterval){
                    trueInterval = 48;
                    break;
                }
            }    
            
            for (Note n : tempList){
                if (trueInterval == 48){
                    tokenDiv = 1;
                    break;
                }
                
                if (tokenDiv == 4){
                    if (n.getPosition() % 4 == 2)
                        tokenDiv = 2;
                    else if (n.getPosition() % 4 == 1){
                        tokenDiv = 1;
                        break;
                    }
                }
                else if (tokenDiv == 2){
                    if (n.getPosition() % 2 == 1){
                        tokenDiv = 1;
                        break;
                    }
                }
            }
            
            //System.out.println("tokenDiv = " + tokenDiv + " trueInterval = " + trueInterval);
            
            for (int j = 0; j < trueInterval; j+=tokenDiv){
                //System.out.println("j = " + j);
                if(tempList.size() != 0){
                    if (tempList.get(0).getPosition() == j){
                        lanes[i] += ((tempList.get(0).getSample() < 27 && tempList.get(0).getSample() > 0) ? "0" : "") + Integer.toString(tempList.get(0).getSample() + 9, 36);
                        tempList.remove(0);
                    }
                    else
                        lanes[i] += "00";
                }
                else lanes[i] += "00";
            }
            System.out.println("result for lane " + i + ": " + lanes[i]);
        }
        
        return lanes;
    }
    
    @Override
    public String toString(){
        String megaString = "";
        for(Note n : noteList)
            megaString += (n.toString() + "\n");
        return megaString;
    }
}