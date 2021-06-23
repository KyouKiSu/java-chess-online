public class Trash {
    static double eloSystem(float elo1, float elo2, boolean draw, boolean win){
        double K = 40;
        double S = 0;
        if(draw){
            S=0.5;
        }
        else{
            if(win){
                S=1;
            }
        }
        return elo1 + K*(S-1/(1+Math.pow(10,(elo2-elo1)/400)));
    }
    public static void main(String[] args) {
        System.out.println(eloSystem(2400,1500,false,false));
    }
}
