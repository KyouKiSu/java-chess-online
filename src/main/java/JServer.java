import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.Side;
import com.github.bhlangonijr.chesslib.move.Move;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Vector;

public class JServer {
    public static void main(String[] args) {
        Server myServer = new Server();
        myServer.startServer();
    }
}

class Server {
    JDataBase dataBase;
    private Vector<User> users;
    public Vector<Game> games;
    private int PORT;

    Server() {
        PORT = 3334;
    }

    Server(int _p) {
        PORT = _p;
    }

    private void initServer() {
        users = new Vector<User>();
        games = new Vector<Game>();
        UserInputHandler.users = users;
        UserInputHandler.games = games;
        UserInputHandler.server = this;
        UserInputHandler.updateAvailableGamesList();
        dataBase = new JDataBase();
    }

    public boolean checkUser(String username, String password) {
        synchronized (dataBase) {
            return dataBase.checkUser(username, password);
        }
    }

    public void startServer() {
        initServer();
        try {
            ServerSocket ss = new ServerSocket(PORT);
            System.out.println("[SYSTEM] Server is running");
            while (true) {
                Socket s = ss.accept();
                UserInputHandler p =
                        new UserInputHandler(s);
                UserHeartBeat h = new UserHeartBeat(p.getUser());
                p.start();
                h.start();
            }

        } catch (Exception e) {
            System.out.println("[SYSTEM] [ERROR] Server down!");
            System.out.println(e);
        }
    }
}

class UserInputHandler extends Thread {
    static Server server;
    private User user;
    static Vector<User> users;
    static Vector<Game> games;
    private final int TIMEOUT = 10000;
    private Game currentGame = null;


    public void removeUser() {
        System.out.println("REMOVING USER");
        boolean changed = false;
        if (currentGame != null) {
            System.out.println("STATE"+currentGame.active);
            if(currentGame.active==1){
                boolean ddraw=false;
                boolean wwhite=false;
                System.out.println("GAME END");
                System.out.println("TOTAL MOVES: "+user.currentlyPlaying.board.getHistory().size());

                if(!user.currentlyPlaying.nameCreator.equals(user.username)){
                    System.out.println("WHITE WINS");
                    user.currentlyPlaying.creatorWin();
                    synchronized(user.currentlyPlaying.creator.outputUser){
                        try{
                            user.currentlyPlaying.creator.outputUser.writeInt(RequestType.GAME_RESULTS);
                            user.currentlyPlaying.creator.outputUser.writeBoolean(ddraw);
                            user.currentlyPlaying.creator.outputUser.writeBoolean(wwhite);
                        }
                        catch (java.net.SocketTimeoutException e) {
                            System.out.println("[USER]" + user.currentlyPlaying.creator.getData() + " [ERROR] no response. Disconnected.");
                            user.currentlyPlaying.creator.active = false;
                        } catch (java.io.EOFException e) {
                            System.out.println("[USER]" + user.currentlyPlaying.creator.getData() + " [ERROR] Client closed stream. Disconnected.");
                            user.currentlyPlaying.creator.active = false;
                        } catch (Exception e) {
                            System.out.println("[USER]" + user.currentlyPlaying.creator.getData() + " [ERROR] Unknown error occurred");
                            System.out.println(e);
                            user.currentlyPlaying.creator.active = false;
                        }

                    }
                    wwhite=true;
                }
                else{
                    System.out.println("BLACK WINS");
                    user.currentlyPlaying.opponentWin();
                    synchronized(user.currentlyPlaying.opponent.outputUser){
                        try{
                            user.currentlyPlaying.opponent.outputUser.writeInt(RequestType.GAME_RESULTS);
                            user.currentlyPlaying.opponent.outputUser.writeBoolean(ddraw);
                            user.currentlyPlaying.opponent.outputUser.writeBoolean(wwhite);
                        }
                        catch (java.net.SocketTimeoutException e) {
                            System.out.println("[USER]" + user.currentlyPlaying.opponent.getData() + " [ERROR] no response. Disconnected.");
                            user.currentlyPlaying.opponent.active = false;
                        } catch (java.io.EOFException e) {
                            System.out.println("[USER]" + user.currentlyPlaying.opponent.getData() + " [ERROR] Client closed stream. Disconnected.");
                            user.currentlyPlaying.opponent.active = false;
                        } catch (Exception e) {
                            System.out.println("[USER]" + user.currentlyPlaying.opponent.getData() + " [ERROR] Unknown error occurred");
                            System.out.println(e);
                            user.currentlyPlaying.opponent.active = false;
                        }

                    }
                }
                synchronized(server.dataBase){
                    server.dataBase.gameEnd(user.currentlyPlaying.nameCreator,user.currentlyPlaying.nameOpponent,ddraw,wwhite);
                }
                clearGameAndUsers(user.currentlyPlaying.creator,user.currentlyPlaying.opponent);
                updateAvailableGamesList();
                listBroadcast();
            }
            if (currentGame != null) {
                if (games.contains(currentGame)) {
                    games.remove(currentGame);
                    changed=true;
                }
                currentGame = null;
            }
        }

        if(changed){
            updateAvailableGamesList();
            listBroadcast();
        }

        user.close(); // close streams and socket
        // say his opponent if he is playing - he won
        synchronized (users) {
            users.remove(user);
        }
        System.out.println("[USER] Stopped " + user.getData());
    }

    public UserInputHandler(Socket _s) throws SocketException {
        _s.setSoTimeout(TIMEOUT);
        // get login and password function
        // if correct - pass, else - break connection instantly after
        // creation
        user = new User(_s, "empty");
        synchronized (users) {
            users.add(user);
        }
    }

    public static void listBroadcast() {
        if (!users.isEmpty()) {
            for (User a : users) {
                sendListInfo(a);
            }
        }
        //send actual list of available games
    }

    public static void sendListInfo(User u) {
        try {
            synchronized (u.outputUser) {
                u.outputUser.writeInt(RequestType.GAME_LIST);
            }
            sendPersonal(u);
            sendAvailableGamesList(u);
            synchronized (u.outputUser) {
                u.outputUser.flush();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void sendPersonal(User u) {
        DataBaseUser dataUser;
        synchronized (server.dataBase) {
            dataUser = server.dataBase.getUser(u.username);
        }
        synchronized (u.outputUser) {
            try {
                u.outputUser.writeUTF(dataUser.username);
                u.outputUser.writeFloat(dataUser.elo);
                u.outputUser.writeInt(dataUser.wins);
                u.outputUser.writeInt(dataUser.loses);
                u.outputUser.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void updateAvailableGamesList() {
        if (Game.availableGames == null) {
            Game.availableGames = new Vector<Game>();
        }
        synchronized (Game.availableGames) {
            if (Game.availableGames.size() != 0) {
                Game.availableGames.clear();
            }
            synchronized (games) {
                if (games != null) {
                    if (games.size() != 0) {
                        for (Game g : games) {
                            if (g.active == 0) {
                                Game.availableGames.add(g);
                            }
                        }
                    }
                }
            }
        }
        System.out.println("[AVAILABLE GAMES] "+Game.availableGames.size());
    }

    public static void sendAvailableGamesList(User u) {
        try {
            synchronized (u.outputUser) {
                synchronized (Game.availableGames) {
                    u.outputUser.writeInt(Game.availableGames.size());
                    for (int i = 0; i < Game.availableGames.size(); i++) {
                        u.outputUser.writeUTF(Game.availableGames.elementAt(i).nameCreator);
                        u.outputUser.writeInt(Game.availableGames.elementAt(i).timeControlTotal);
                        u.outputUser.writeInt(Game.availableGames.elementAt(i).timeControlIncrement);
                        u.outputUser.writeFloat(Game.availableGames.elementAt(i).eloCreator);
                    }
                }
                u.outputUser.flush();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        System.out.println("[USER] Started new client " + user.getData());
        String message;
        String response;
        try {
            int type = user.inputUser.readInt();
            String username = user.inputUser.readUTF();
            String password = user.inputUser.readUTF();
            if (server.checkUser(username, password)) {
                user.username = username;
                System.out.println("[LOGIN] Good user entered");
                user.outputUser.writeInt(RequestType.LOGIN);
                user.outputUser.flush();
                sendListInfo(user);
            } else {
                System.out.println("[LOGIN] Bad user tried to enter");
                user.outputUser.writeInt(RequestType.LOGIN_ERROR);
                user.outputUser.flush();
                user.active = false;
                removeUser();
                return;
            }
        } catch (IOException e) {
            user.active = false;
            removeUser();
            return;
        }
        while (user.active) {
            try {
                synchronized (user.inputUser) {
                    int type = user.inputUser.readInt();
                    switch (type) {
                        case RequestType.LOGIN:
                            // todo REGISTER
                            break;
                        case RequestType.LEADERBOARDS:
                            Vector<DataBaseUser> topUsers;
                            synchronized (server.dataBase) {
                                topUsers = server.dataBase.topUsers();
                            }
                            synchronized (user.outputUser) {
                                user.outputUser.writeInt(RequestType.LEADERBOARDS);
                                user.outputUser.writeInt(topUsers.size());
                                for (int i = 0; i < topUsers.size(); i++) {
                                    user.outputUser.writeUTF(topUsers.elementAt(i).username);
                                    user.outputUser.writeFloat(topUsers.elementAt(i).elo);
                                    user.outputUser.writeInt(topUsers.elementAt(i).wins);
                                    user.outputUser.writeInt(topUsers.elementAt(i).loses);
                                }
                            }
                            System.out.println("[USER]" + user.getData() + " requested LEADERBOARDS");
                            break;
                        case RequestType.GAME_CREATE:
                            int total = user.inputUser.readInt();
                            int increment = user.inputUser.readInt();
                            DataBaseUser u;
                            synchronized (server.dataBase) {
                                u = server.dataBase.getUser(user.username);
                            }
                            synchronized (games) {
                                currentGame = new Game(user, u.elo, total, increment);
                                games.add(currentGame);
                            }
                            synchronized (user.outputUser) {
                                user.outputUser.writeInt(RequestType.GAME_CREATE);
                            }
                            updateAvailableGamesList();
                            listBroadcast();
                            System.out.println("[USER]" + user.getData() + " created new game");
                            break;

                        case RequestType.GAME_JOIN:
                            String name = user.inputUser.readUTF();
                            System.out.println("Name:"+name);
                            Game game = null;
                            synchronized (games){
                                for (Game g:games) {
                                    System.out.println("NAME:"+g.nameCreator);
                                    if(g.nameCreator.equals(name)){
                                        game=g;
                                        break;
                                    }
                                }
                            }
                            if(game==null){
                                System.out.println("[USER]"+ user.getData() +" [ERROR] no game in list");
                            }
                            else{
                                float elo = 0;
                                synchronized (server.dataBase) {
                                    DataBaseUser b;
                                    elo = server.dataBase.getUser(user.username).elo;
                                }
                                game.opponentConnect(user,elo);
                                callGameEvent(game);
                            }

                            break;

                        case RequestType.GAME_CANCEL_WAITING:
                            if (currentGame != null) {
                                if (games.contains(currentGame)) {
                                    games.remove(currentGame);
                                }
                                currentGame = null;
                            }
                            System.out.println("[USER] " + user.getData() + " aborted game");
                            updateAvailableGamesList();
                            listBroadcast();
                            break;
                        case RequestType.PING_TYPE:
                            //System.out.println("Successful ping of user " + user.getData());
                            break;
                        case RequestType.GAME_RESIGN:
                            boolean ddraw=false;
                            boolean wwhite=false;
                            System.out.println("GAME END");
                            System.out.println("TOTAL MOVES: "+user.currentlyPlaying.board.getHistory().size());

                            if(!user.currentlyPlaying.nameCreator.equals(user.username)){
                                System.out.println("WHITE WINS");
                                user.currentlyPlaying.creatorWin();
                                synchronized(user.currentlyPlaying.creator.outputUser){
                                    try{
                                        user.currentlyPlaying.creator.outputUser.writeInt(RequestType.GAME_RESULTS);
                                        user.currentlyPlaying.creator.outputUser.writeBoolean(ddraw);
                                        user.currentlyPlaying.creator.outputUser.writeBoolean(wwhite);
                                    }
                                    catch (java.net.SocketTimeoutException e) {
                                        System.out.println("[USER]" + user.currentlyPlaying.creator.getData() + " [ERROR] no response. Disconnected.");
                                        user.currentlyPlaying.creator.active = false;
                                    } catch (java.io.EOFException e) {
                                        System.out.println("[USER]" + user.currentlyPlaying.creator.getData() + " [ERROR] Client closed stream. Disconnected.");
                                        user.currentlyPlaying.creator.active = false;
                                    } catch (Exception e) {
                                        System.out.println("[USER]" + user.currentlyPlaying.creator.getData() + " [ERROR] Unknown error occurred");
                                        System.out.println(e);
                                        user.currentlyPlaying.creator.active = false;
                                    }

                                }
                                wwhite=true;
                            }
                            else{
                                System.out.println("BLACK WINS");
                                user.currentlyPlaying.opponentWin();
                                synchronized(user.currentlyPlaying.opponent.outputUser){
                                    try{
                                        user.currentlyPlaying.opponent.outputUser.writeInt(RequestType.GAME_RESULTS);
                                        user.currentlyPlaying.opponent.outputUser.writeBoolean(ddraw);
                                        user.currentlyPlaying.opponent.outputUser.writeBoolean(wwhite);
                                    }
                                    catch (java.net.SocketTimeoutException e) {
                                        System.out.println("[USER]" + user.currentlyPlaying.opponent.getData() + " [ERROR] no response. Disconnected.");
                                        user.currentlyPlaying.opponent.active = false;
                                    } catch (java.io.EOFException e) {
                                        System.out.println("[USER]" + user.currentlyPlaying.opponent.getData() + " [ERROR] Client closed stream. Disconnected.");
                                        user.currentlyPlaying.opponent.active = false;
                                    } catch (Exception e) {
                                        System.out.println("[USER]" + user.currentlyPlaying.opponent.getData() + " [ERROR] Unknown error occurred");
                                        System.out.println(e);
                                        user.currentlyPlaying.opponent.active = false;
                                    }

                                }
                            }
                            synchronized(server.dataBase){
                                server.dataBase.gameEnd(user.currentlyPlaying.nameCreator,user.currentlyPlaying.nameOpponent,ddraw,wwhite);
                            }
                            clearGameAndUsers(user.currentlyPlaying.creator,user.currentlyPlaying.opponent);
                            updateAvailableGamesList();
                            listBroadcast();

                            break;
                        case RequestType.GAME_TIMEOUT:
                            boolean c = user.inputUser.readBoolean();
                            boolean result = user.currentlyPlaying.checkTime(c);
                            if(result){
                                boolean Tdraw=false;
                                boolean Twhite=false;
                                System.out.println("GAME END");
                                System.out.println("TOTAL MOVES: "+user.currentlyPlaying.board.getHistory().size());
                                if(user.currentlyPlaying.board.isDraw()){
                                    System.out.println("DRAW");
                                    user.currentlyPlaying.draw();
                                    Tdraw= true;
                                }
                                else{
                                    if(user.currentlyPlaying.creatorWon){
                                        System.out.println("WHITE WINS");
                                        Twhite=true;
                                    }
                                    else{
                                        System.out.println("BLACK WINS");
                                    }
                                }


                                synchronized(server.dataBase){
                                    server.dataBase.gameEnd(user.currentlyPlaying.nameCreator,user.currentlyPlaying.nameOpponent,Tdraw,Twhite);
                                }
                                synchronized(user.currentlyPlaying.creator.outputUser){
                                    try{
                                        user.currentlyPlaying.creator.outputUser.writeInt(RequestType.GAME_RESULTS);
                                        user.currentlyPlaying.creator.outputUser.writeBoolean(Tdraw);
                                        user.currentlyPlaying.creator.outputUser.writeBoolean(Twhite);
                                    }
                                    catch (java.net.SocketTimeoutException e) {
                                        System.out.println("[USER]" + user.currentlyPlaying.creator.getData() + " [ERROR] no response. Disconnected.");
                                        user.currentlyPlaying.creator.active = false;
                                    } catch (java.io.EOFException e) {
                                        System.out.println("[USER]" + user.currentlyPlaying.creator.getData() + " [ERROR] Client closed stream. Disconnected.");
                                        user.currentlyPlaying.creator.active = false;
                                    } catch (Exception e) {
                                        System.out.println("[USER]" + user.currentlyPlaying.creator.getData() + " [ERROR] Unknown error occurred");
                                        System.out.println(e);
                                        user.currentlyPlaying.creator.active = false;
                                    }

                                }
                                synchronized(user.currentlyPlaying.opponent.outputUser){
                                    try{
                                        user.currentlyPlaying.opponent.outputUser.writeInt(RequestType.GAME_RESULTS);
                                        user.currentlyPlaying.opponent.outputUser.writeBoolean(Tdraw);
                                        user.currentlyPlaying.opponent.outputUser.writeBoolean(Twhite);
                                    }
                                    catch (java.net.SocketTimeoutException e) {
                                        System.out.println("[USER]" + user.currentlyPlaying.opponent.getData() + " [ERROR] no response. Disconnected.");
                                        user.currentlyPlaying.opponent.active = false;
                                    } catch (java.io.EOFException e) {
                                        System.out.println("[USER]" + user.currentlyPlaying.opponent.getData() + " [ERROR] Client closed stream. Disconnected.");
                                        user.currentlyPlaying.opponent.active = false;
                                    } catch (Exception e) {
                                        System.out.println("[USER]" + user.currentlyPlaying.opponent.getData() + " [ERROR] Unknown error occurred");
                                        System.out.println(e);
                                        user.currentlyPlaying.opponent.active = false;
                                    }

                                }

                                clearGameAndUsers(user.currentlyPlaying.creator,user.currentlyPlaying.opponent);
                                updateAvailableGamesList();
                                listBroadcast();
                            }
                            break;
                        case RequestType.GAME_TYPE:
                            String moveString = user.inputUser.readUTF();
                            // check if user is playing
                            // check if game is running
                            // check if move is legal
                            // send request with move
                            if(user.currentlyPlaying!=null){
                                if(user.username.equals(user.currentlyPlaying.creator.username) && user.currentlyPlaying.turn==true ||
                                        user.username.equals(user.currentlyPlaying.opponent.username) && user.currentlyPlaying.turn==false){
                                    Move move = new Move(moveString, Side.WHITE);

                                    if(user.currentlyPlaying.board.legalMoves().contains(move)){
                                        user.currentlyPlaying.board.doMove(move);
                                        System.out.println("MOVE:"+move.toString());
                                        user.currentlyPlaying.switchClock(user.username);
                                        // send both players BOARD and TIME
                                        synchronized(user.currentlyPlaying.creator.outputUser){
                                            try{
                                                user.currentlyPlaying.creator.outputUser.writeInt(RequestType.GAME_TYPE);
                                                user.currentlyPlaying.creator.outputUser.writeUTF(move.toString());
                                                user.currentlyPlaying.creator.outputUser.writeBoolean(user.currentlyPlaying.turn);
                                                user.currentlyPlaying.creator.outputUser.writeInt(user.currentlyPlaying.creatorTimeLeft);
                                                user.currentlyPlaying.creator.outputUser.writeInt(user.currentlyPlaying.opponentTimeLeft);
                                            }
                                            catch (java.net.SocketTimeoutException e) {
                                                System.out.println("[USER]" + user.currentlyPlaying.creator.getData() + " [ERROR] no response. Disconnected.");
                                                user.currentlyPlaying.creator.active = false;
                                            } catch (java.io.EOFException e) {
                                                System.out.println("[USER]" + user.currentlyPlaying.creator.getData() + " [ERROR] Client closed stream. Disconnected.");
                                                user.currentlyPlaying.creator.active = false;
                                            } catch (Exception e) {
                                                System.out.println("[USER]" + user.currentlyPlaying.creator.getData() + " [ERROR] Unknown error occurred");
                                                System.out.println(e);
                                                user.currentlyPlaying.creator.active = false;
                                            }

                                        }
                                        synchronized(user.currentlyPlaying.opponent.outputUser){
                                            try{
                                                user.currentlyPlaying.opponent.outputUser.writeInt(RequestType.GAME_TYPE);
                                                user.currentlyPlaying.opponent.outputUser.writeUTF(move.toString());
                                                user.currentlyPlaying.opponent.outputUser.writeBoolean(user.currentlyPlaying.turn);
                                                user.currentlyPlaying.opponent.outputUser.writeInt(user.currentlyPlaying.creatorTimeLeft);
                                                user.currentlyPlaying.opponent.outputUser.writeInt(user.currentlyPlaying.opponentTimeLeft);
                                            }
                                            catch (java.net.SocketTimeoutException e) {
                                                System.out.println("[USER]" + user.currentlyPlaying.opponent.getData() + " [ERROR] no response. Disconnected.");
                                                user.currentlyPlaying.opponent.active = false;
                                            } catch (java.io.EOFException e) {
                                                System.out.println("[USER]" + user.currentlyPlaying.opponent.getData() + " [ERROR] Client closed stream. Disconnected.");
                                                user.currentlyPlaying.opponent.active = false;
                                            } catch (Exception e) {
                                                System.out.println("[USER]" + user.currentlyPlaying.opponent.getData() + " [ERROR] Unknown error occurred");
                                                System.out.println(e);
                                                user.currentlyPlaying.opponent.active = false;
                                            }

                                        }
                                        if(user.currentlyPlaying.board.isMated() || user.currentlyPlaying.board.isDraw()){
                                            boolean draw=false;
                                            boolean white=false;
                                            System.out.println("GAME END");
                                            System.out.println("TOTAL MOVES: "+user.currentlyPlaying.board.getHistory().size());
                                            if(user.currentlyPlaying.board.isDraw()){
                                                System.out.println("DRAW");
                                                user.currentlyPlaying.draw();
                                                draw= true;
                                            }
                                            else{
                                                if(user.currentlyPlaying.board.getHistory().size()%2==0){
                                                    System.out.println("WHITE WINS");
                                                    user.currentlyPlaying.creatorWin();
                                                    white=true;
                                                }
                                                else{
                                                    System.out.println("BLACK WINS");
                                                    user.currentlyPlaying.opponentWin();
                                                }
                                            }


                                            synchronized(server.dataBase){
                                                server.dataBase.gameEnd(user.currentlyPlaying.nameCreator,user.currentlyPlaying.nameOpponent,draw,white);
                                            }
                                            synchronized(user.currentlyPlaying.creator.outputUser){
                                                try{
                                                    user.currentlyPlaying.creator.outputUser.writeInt(RequestType.GAME_RESULTS);
                                                    user.currentlyPlaying.creator.outputUser.writeBoolean(draw);
                                                    user.currentlyPlaying.creator.outputUser.writeBoolean(white);
                                                }
                                                catch (java.net.SocketTimeoutException e) {
                                                    System.out.println("[USER]" + user.currentlyPlaying.creator.getData() + " [ERROR] no response. Disconnected.");
                                                    user.currentlyPlaying.creator.active = false;
                                                } catch (java.io.EOFException e) {
                                                    System.out.println("[USER]" + user.currentlyPlaying.creator.getData() + " [ERROR] Client closed stream. Disconnected.");
                                                    user.currentlyPlaying.creator.active = false;
                                                } catch (Exception e) {
                                                    System.out.println("[USER]" + user.currentlyPlaying.creator.getData() + " [ERROR] Unknown error occurred");
                                                    System.out.println(e);
                                                    user.currentlyPlaying.creator.active = false;
                                                }

                                            }
                                            synchronized(user.currentlyPlaying.opponent.outputUser){
                                                try{
                                                    user.currentlyPlaying.opponent.outputUser.writeInt(RequestType.GAME_RESULTS);
                                                    user.currentlyPlaying.opponent.outputUser.writeBoolean(draw);
                                                    user.currentlyPlaying.opponent.outputUser.writeBoolean(white);
                                                }
                                                catch (java.net.SocketTimeoutException e) {
                                                    System.out.println("[USER]" + user.currentlyPlaying.opponent.getData() + " [ERROR] no response. Disconnected.");
                                                    user.currentlyPlaying.opponent.active = false;
                                                } catch (java.io.EOFException e) {
                                                    System.out.println("[USER]" + user.currentlyPlaying.opponent.getData() + " [ERROR] Client closed stream. Disconnected.");
                                                    user.currentlyPlaying.opponent.active = false;
                                                } catch (Exception e) {
                                                    System.out.println("[USER]" + user.currentlyPlaying.opponent.getData() + " [ERROR] Unknown error occurred");
                                                    System.out.println(e);
                                                    user.currentlyPlaying.opponent.active = false;
                                                }

                                            }

                                            clearGameAndUsers(user.currentlyPlaying.creator,user.currentlyPlaying.opponent);
                                            updateAvailableGamesList();
                                            listBroadcast();
                                        }
                                    }else{
                                        System.out.println("[USER] " + user.getData() + " [ERROR] Bad move" );
                                    }
                                }
                                else{
                                    System.out.println("[USER] " + user.getData() + " [ERROR] Not your turn to move" );
                                }
                            }
                            break;
                        default:
                            System.out.println("[USER] " + user.getData() + " [ERROR] Type is " + type + ". Bad request type. Kill connection.");
                            user.active = false;
                    }
                }
            } catch (java.net.SocketTimeoutException e) {
                System.out.println("[USER]" + user.getData() + " [ERROR] no response. Disconnected.");
                user.active = false;
            } catch (java.io.EOFException e) {
                System.out.println("[USER]" + user.getData() + " [ERROR] Client closed stream. Disconnected.");
                user.active = false;
            } catch (Exception e) {
                System.out.println("[USER]" + user.getData() + " [ERROR] Unknown error occurred");
                System.out.println(e);
                user.active = false;
            }

        }
        removeUser();
    }

    public void clearGameAndUsers(User p1, User p2){
        games.remove(p1.currentlyPlaying);
        p1.currentlyPlaying=null;
        p2.currentlyPlaying=null;
    }

    public User getUser() {
        return user;
    }

    private void callGameEvent(Game game){
        // todo ON DISCONNECT - CLEAR GAME
        // todo ON START REMOVE GAME FROM AVAILABLE
        game.creator.currentlyPlaying = game;
        game.opponent.currentlyPlaying = game;
        updateAvailableGamesList();
        listBroadcast();
        synchronized (game.creator.outputUser){
            try {
                game.creator.outputUser.writeInt(RequestType.GAME_START);
                game.creator.outputUser.writeUTF(game.opponent.username);
                game.creator.outputUser.flush();
            } catch (java.net.SocketTimeoutException e) {
                System.out.println("[USER]" + game.creator.getData() + " [ERROR] Client  no response. Disconnected.");
                game.creator.active = false;
            } catch (java.io.EOFException e) {
                System.out.println("[USER]" + game.creator.getData() + " [ERROR] Client  closed stream. Disconnected.");
                game.creator.active = false;
            } catch (Exception e) {
                System.out.println("[USER]" + game.creator.getData() + " [ERROR] Unknown error occurred");
                System.out.println(e);
                game.creator.active = false;
            }
        }
        synchronized (game.opponent.outputUser){
            try {
                game.opponent.outputUser.writeInt(RequestType.GAME_START);
                game.opponent.outputUser.writeUTF(game.creator.username);
                game.opponent.outputUser.flush();
            } catch (java.net.SocketTimeoutException e) {
                System.out.println("[USER]" + game.opponent.getData() + " [ERROR] Client  no response. Disconnected.");
                game.opponent.active = false;
            } catch (java.io.EOFException e) {
                System.out.println("[USER]" + game.opponent.getData() + " [ERROR] Client  closed stream. Disconnected.");
                game.opponent.active = false;
            } catch (Exception e) {
                System.out.println("[USER]" + game.opponent.getData() + " [ERROR] Unknown error occurred");
                System.out.println(e);
                game.opponent.active = false;
            }
        }
    }
}


class Game {
    User creator;
    User opponent;
    Board board;
    static Vector<Game> availableGames;
    public int active; // 0 waiting for opponent, 1 playing, 2 ended
    public boolean creatorWon;
    public boolean draw;
    public String nameCreator;
    public String nameOpponent;
    public float eloCreator;
    public float eloOpponent;
    public int timeControlTotal; // total
    public int timeControlIncrement; // increment

    public int creatorTimeLeft;
    public long clockCreatorMoved;
    public int opponentTimeLeft;
    public long clockOpponentMoved;

    public boolean turn; // true - creator to move, false - opponent

    Game(User _c, float _ec, int _tt, int _ti) {
        creator = _c;
        turn = true;
        nameCreator = creator.username;
        eloCreator = _ec;
        timeControlTotal = _tt;
        timeControlIncrement = _ti;
        opponent=null;
        nameOpponent = "";
        eloOpponent = -1;
        active = 0;
        creatorWon = false;
        draw=false;
        creatorTimeLeft = timeControlTotal*60;
        opponentTimeLeft = timeControlTotal*60;
        clockCreatorMoved = 0;
        clockOpponentMoved = 0;
    }

    public void opponentConnect(User _o, float _eo) {
        opponent = _o;
        nameOpponent = opponent.username;
        eloOpponent = _eo;
        active = 1;
        board = new Board();
    }

    public boolean checkTime(boolean creator){
        boolean result = false;
        int POSSIBLEcreatorTimeLeft=1;
        int POSSIBLEopponentTimeLeft=1;
        if(creator){
            long moveTime = System.nanoTime() - clockOpponentMoved; System.out.println("MOVE TIME:"+moveTime/ 1_000_000_000);
            POSSIBLEcreatorTimeLeft = (int) ((creatorTimeLeft * 1_000_000_000.0 - moveTime) / 1_000_000_000);
        }
        else{
            long moveTime = System.nanoTime() - clockCreatorMoved; System.out.println("MOVE TIME:"+moveTime/ 1_000_000_000);
            POSSIBLEopponentTimeLeft = (int) ((opponentTimeLeft * 1_000_000_000.0 - moveTime) / 1_000_000_000);
        }
        if(POSSIBLEcreatorTimeLeft < 0 || POSSIBLEopponentTimeLeft < 0){
            if(POSSIBLEcreatorTimeLeft < 0){
                opponentWin();
                result= true;
            }
            if(POSSIBLEopponentTimeLeft < 0){
                creatorWin();
                result= true;
            }
        }
        return result;
    }

    public void switchClock(String username) {
        // check if move is legal
        if(active == 2){
            return;
        }
        if (username.equals(nameCreator)) {
            if (clockCreatorMoved == 0) {
                clockCreatorMoved = System.nanoTime();
            } else {
                clockCreatorMoved = System.nanoTime();
                long moveTime = System.nanoTime() - clockOpponentMoved; System.out.println("MOVE TIME:"+moveTime/ 1_000_000_000);
                System.out.println("C TIME:"+creatorTimeLeft);
                creatorTimeLeft = (int) ((creatorTimeLeft * 1_000_000_000.0 - moveTime + timeControlIncrement * 1_000_000_000.0) / 1_000_000_000);
                System.out.println("C TIME:"+creatorTimeLeft);
            }
        }
        if (username.equals(nameOpponent)) {
            if (clockOpponentMoved == 0) {
                clockOpponentMoved = System.nanoTime();
            } else {
                clockOpponentMoved = System.nanoTime();
                long moveTime = System.nanoTime() - clockCreatorMoved; System.out.println("MOVE TIME:"+moveTime/ 1_000_000_000);
                System.out.println("O TIME:"+opponentTimeLeft);
                opponentTimeLeft = (int) ((opponentTimeLeft * 1_000_000_000.0 - moveTime + timeControlIncrement * 1_000_000_000.0) / 1_000_000_000);
                System.out.println("O TIME:"+opponentTimeLeft);
            }
        }
        turn = !turn;
        System.out.println("TURN:"+turn);
        System.out.println("CREATOR:"+creatorTimeLeft);
        System.out.println("OPPONENT:"+opponentTimeLeft);
        System.out.println("--------");
        if (opponentTimeLeft <= 0) {
            opponentWin();
        }
        if (creatorTimeLeft <= 0) {
            creatorWin();
        }
    }

    public void opponentWin() {
        System.out.println("BLACK WON");
        creatorWon = false;
        active = 2;
        //SEND EVENTS
    }

    public void creatorWin() {
        System.out.println("WHITE WON");
        creatorWon = true;
        active = 2;
        //SEND EVENTS
    }
    public void draw(){
        System.out.println("DRAW");
        draw = false;
        active = 2;
        //SEND EVENTS
    }

    // check outside - is game ended. and do things
}

class UserHeartBeat extends Thread {
    private static final long PERIOD = 5000;
    User user;

    UserHeartBeat(User _u) {
        user = _u;
    }

    public void ping() {
        boolean result = false;
        try {
            synchronized (user.outputUser) {
                user.outputUser.writeInt(RequestType.PING_TYPE);
                user.outputUser.flush();
            }
        } catch (Exception e) {
            System.out.println(e);
        }
    }

    @Override
    public void run() {
        while (user.active) {
            try {
                ping();
                sleep(PERIOD);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}

class User {
    public String username;
    public Socket socket;
    public Game currentlyPlaying;
    public DataInputStream inputUser;
    public DataOutputStream outputUser;
    public boolean active;

    User(Socket _s, String _u) {
        username = _u;
        socket = _s;
        try {
            inputUser = new DataInputStream(socket.getInputStream());
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            outputUser = new DataOutputStream(socket.getOutputStream());
        } catch (IOException e) {
            e.printStackTrace();
        }
        currentlyPlaying = null;
        active = true;
    }

    public String getData() {
        return "" + socket.getInetAddress() + ":" + socket.getPort();
    }

    public void close() {
        try {
            inputUser.close();
        } catch (IOException e) {
            System.out.println("[USER] [ERROR] Failed to close inputUser");
        }
        try {
            outputUser.close();
        } catch (IOException e) {
            System.out.println("[USER] [ERROR] Failed to close outputUser");
        }
        try {
            socket.close();
        } catch (IOException e) {
            System.out.println("[USER] [ERROR] Failed to close socket");
        }
    }
}
