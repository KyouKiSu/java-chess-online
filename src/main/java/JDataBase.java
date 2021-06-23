import java.sql.*;
import java.util.Vector;


public class JDataBase {
    private Connection connect = null;
    private PreparedStatement psInsert = null;
    private PreparedStatement psCheck = null;
    private PreparedStatement psUpdate=null;
    private ResultSet resultSet = null;
    private String url = "jdbc:mysql://localhost:3306/mydbtest";
    private String user = "root", pass = "root";
    private static final String INSERT_NEW = "INSERT INTO chess_users VALUES(?,?,?,?,?)";
    private static final String CHECK_USER = "SELECT EXISTS(SELECT * from chess_users WHERE username=? AND password=?)";
    private static final String GET_USER = "select * from chess_users where username=?";
    private static final  String UPDATE_USER ="update chess_users set wins=?,loses=?,elo=? where username=?";

    JDataBase(){
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        try{
            connect = DriverManager.getConnection(url, user, pass);
            //psInsert = connect.prepareStatement(INSERT_NEW);
            //psCheck = connect.prepareStatement(CHECK_USER);
        }
        catch (SQLException e) {
            e.printStackTrace();
        }
    }
    public void close(){
        try {
            connect.close();
        } catch (SQLException throwable) {
            throwable.printStackTrace();
        }
    }

    public void addNewUser(String username,String password, int wins, int loses, double elo){
        try {
            psInsert = connect.prepareStatement(INSERT_NEW);
            psCheck.setString(1,username);
            psCheck.setString(2,password);
            psCheck.setFloat(3,(float)elo);
            psCheck.setInt(4,wins);
            psCheck.setInt(5,loses);

            psCheck.execute();
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }
    }

    public void updateUser(String username,int wins,int loses,double elo){
        try {
            psUpdate = connect.prepareStatement(UPDATE_USER);
            psUpdate.setInt(1,wins);
            psUpdate.setInt(2,loses);
            psUpdate.setFloat(3,(float)elo);
            psUpdate.setString(4,username);
            psUpdate.execute();
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }

    }

    public boolean checkUser(String username,String password){
        try {
            psCheck = connect.prepareStatement(CHECK_USER);
            psCheck.setString(1,username);
            psCheck.setString(2,password);

            ResultSet rs = psCheck.executeQuery();
            rs.next();
            return rs.getBoolean(1);
        } catch (SQLException throwables) {
            throwables.printStackTrace();
            return false;
        }
    }

    public DataBaseUser getUser(String username){
        try {
            psCheck = connect.prepareStatement(GET_USER);
            psCheck.setString(1,username);

            ResultSet rs = psCheck.executeQuery();
            rs.next();
            return new DataBaseUser(rs.getString(2),rs.getFloat(4),rs.getInt(5),rs.getInt(6));
        } catch (SQLException throwables) {
            throwables.printStackTrace();
            return null;
        }
    }

    public Vector<DataBaseUser> topUsers(){
        Vector<DataBaseUser> results=new Vector<>();
        String query = "select * from chess_users order by elo DESC, wins DESC";
        try{
            Statement statement = connect.createStatement();
            ResultSet resultSet = statement.executeQuery(query);
            while (resultSet.next()){
                results.add(new DataBaseUser(resultSet.getString(2),resultSet.getFloat(4),resultSet.getInt(5),resultSet.getInt(6)));
            }
        } catch (SQLException throwables) {
            System.out.println("[ERROR] couldn't get top users");
        }
        return results;
    }

    private static double eloSystem(float elo1, float elo2, boolean draw, boolean win){
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

    public void gameEnd(String p1, String p2, boolean draw, boolean white){
        DataBaseUser player1 = getUser(p1);
        DataBaseUser player2 = getUser(p2);
        double player1NewElo = eloSystem(player1.elo, player2.elo, draw,white);
        double player2NewElo = eloSystem(player2.elo, player1.elo, draw,!white);
        System.out.println(player1.username+" "+player1NewElo);
        System.out.println(player2.username+" "+player2NewElo);
        if(draw){
            System.out.println("d DRAW");
            updateUser(player1.username,player1.wins,player1.loses,player1NewElo);
            updateUser(player2.username,player2.wins,player2.loses,player2NewElo);
        }
        else{
            if(white){
                System.out.println("d WHITE");
                updateUser(player1.username,player1.wins+1,player1.loses,player1NewElo);
                updateUser(player2.username,player2.wins,player2.loses+1,player2NewElo);
            }
            else{
                System.out.println("d BLACK");
                updateUser(player1.username,player1.wins,player1.loses+1,player1NewElo);
                updateUser(player2.username,player2.wins+1,player2.loses,player2NewElo);
            }
        }
    }
}

class DataBaseUser{
    public final String username;

    public final float elo;
    public final int wins, loses;
    DataBaseUser(String _u,float _e,int _w, int _l){
        username=_u;
        elo = _e;
        wins=_w;
        loses=_l;
    }
}
