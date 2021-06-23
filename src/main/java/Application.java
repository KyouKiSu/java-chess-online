import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.Piece;
import com.github.bhlangonijr.chesslib.Side;
import com.github.bhlangonijr.chesslib.Square;
import com.github.bhlangonijr.chesslib.move.Move;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

public class Application extends JFrame {
    final int xWindowSize = 666, yWindowSize = 512;
    final private JPanel mainPanel;
    JTextArea loginField;
    JPasswordField passwordField;
    private JPanel listPanel;
    private JPanel listPanelGameListSection;
    private ListPanelRightSideBar listPanelRightBar;
    private DataGamePanel availableGamesPanel;
    private TexturePanel menuPanel;
    private JPanel leaderboardsPanel;
    private JPanel topPanel;
    private LeaderPanelRightSideBar leaderboardsPanelRightBar;
    private DataUserPanel topUsersPanel;
    private PackageHandler serverHandler;
    private JPanel gamePanel;
    private JPanel creationPanel;
    private JPanel waitingPanel;
    private JPanel resultsPanel;
    private JLabel resultsLabel;
    private JButton resultsExitButton;
    private String myUsername;
    private float myElo;
    private int myWins;
    private int myLoses;
    private Vector<DatumUser> topUsers;
    private String opponentName;
    private float opponentElo;
    private int timeIncrement;
    private Vector<DatumGame> availableGames;
    private String[] cards = {"Menu", "List", "LeaderBoards", "Game", "Creation", "Waiting", "Results"};
    public static void setUIFont (javax.swing.plaf.FontUIResource f){
        java.util.Enumeration keys = UIManager.getDefaults().keys();
        while (keys.hasMoreElements()) {
            Object key = keys.nextElement();
            Object value = UIManager.get (key);
            if (value instanceof javax.swing.plaf.FontUIResource)
                UIManager.put (key, f);
        }
    }
    private Integer[] timeTotalArray = {15, 10, 5, 3, 1};
    private Integer[] timeIncrementArray = {10, 5, 3, 2, 1, 0};
    JPanel chessPanel;
    ChessBoardPane chessBoard;
    JPanel chessSidePanel;
    JLabel labelMy;
    JLabel labelOp;
    JButton buttonExit;
    JLabel labelTimeMy;
    JLabel labelTimeOp;
    Timer countingTimer;

    static BufferedImage[] imageCollection;
    Vector<ChessBoardPane.Cell> cells;
    static void initImages(){
        String defaultPath = "src/main/sprites/";
        imageCollection = new BufferedImage[12];
        try {
            imageCollection[0]= ImageIO.read(new File(defaultPath+"wP.png"));
            imageCollection[6]=ImageIO.read(new File(defaultPath+"bP.png"));
            imageCollection[1]=ImageIO.read(new File(defaultPath+"wN.png"));
            imageCollection[7]=ImageIO.read(new File(defaultPath+"bN.png"));
            imageCollection[2]=ImageIO.read(new File(defaultPath+"wB.png"));
            imageCollection[8]=ImageIO.read(new File(defaultPath+"bB.png"));
            imageCollection[3]=ImageIO.read(new File(defaultPath+"wR.png"));
            imageCollection[9]=ImageIO.read(new File(defaultPath+"bR.png"));
            imageCollection[4]=ImageIO.read(new File(defaultPath+"wQ.png"));
            imageCollection[10]=ImageIO.read(new File(defaultPath+"bQ.png"));
            imageCollection[5]=ImageIO.read(new File(defaultPath+"wK.png"));
            imageCollection[11]=ImageIO.read(new File(defaultPath+"bK.png"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    int source = -1;
    int destination = -1;
    int piece = -1;
    boolean youCreator = false;
    boolean yourTurn = false;
    int myTime=0;
    int opponentTime=0;

    public Application() {
        super("Chess Online");
        setVisible(false);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        mainPanel = new JPanel();
        mainPanel.setLayout(new CardLayout());
        add(mainPanel, BorderLayout.CENTER);
        mainPanel.setBackground(Color.GRAY);

        setUIFont (new javax.swing.plaf.FontUIResource("Verdana",Font.PLAIN,18));

        mainWindowPlacement();

        initMenuPanel();
        initListPanel();
        initLeaderboardPanel();
        initCreationPanel();
        initWaitingPanel();
        initChessboard();
        initResultsPanel();

        pack();
        setVisible(true);
    }

    public static void main(String[] args) {
        new Application();
    }

    private void loginEvent() {
        // TODO handle client server communications
        Socket sock = null;
        try {
            sock = new Socket("localhost", 3334);
            sock.setSoTimeout(PackageHandler.TIMEOUT);
            DataOutputStream outStream = new DataOutputStream(sock.getOutputStream());
            DataInputStream inputStream = new DataInputStream(sock.getInputStream());
            outStream.writeInt(RequestType.LOGIN);
            outStream.writeUTF(loginField.getText());
            outStream.writeUTF(new String(passwordField.getPassword()));
            outStream.flush();
            int type = inputStream.readInt();
            if (type == RequestType.PING_TYPE) {
                type = inputStream.readInt();
            }
            if (type == RequestType.LOGIN) {
                serverHandler = new PackageHandler(sock, inputStream, outStream);
                serverHandler.start();
                switchLayout(cards[1]);
            } else {
                sock.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void timerKiller(){
        try{
            countingTimer.stop();
        }
        catch(Exception e){

        }
        try{
            countingTimer=null;
        }
        catch(Exception e){

        }
    }

    private void endGameEvent(boolean draw, boolean white) {
        timerKiller();
        if(draw){
            resultsLabel.setText("Nobody won. It's a draw!");
        }
        else{
            if(youCreator && white || !youCreator && !white){
                resultsLabel.setText("You won! Good job!");
            }
            else{
                resultsLabel.setText("You lost.");
            }
        }
        resultsPanel.revalidate();
        switchLayout(cards[6]);
    }

    private void createGameEvent(int total, int increment) {
        serverHandler.askToCreateGame(total,increment);
        youCreator=true;
        yourTurn=true;
        switchLayout(cards[5]);
    }

    private void createSettingsEvent() {
        // nvm, first of all open menu with settings
        switchLayout(cards[4]);
    }

    private void leaderboardEvent() {
        serverHandler.askToShowLeaderBoards();
        switchLayout(cards[2]);
    }

    private void cancelGameEvent(){
        serverHandler.askToCancelCreateGame();
        listEvent();
    }

    private void listEvent() {
        switchLayout(cards[1]);
    }

    private void joinEvent(String name,float elo){
        opponentElo=elo;
        youCreator=false;
        yourTurn=false;
        serverHandler.askToJoinGame(name);
        System.out.println(name);
    }

    private void gameStartEvent(){
        timerKiller();
        chessPanel.remove(chessBoard);
        chessBoard = new ChessBoardPane(youCreator);
        chessPanel.add(chessBoard,BorderLayout.CENTER);
        chessPanel.revalidate();

        labelMy.setText(myUsername);
        labelOp.setText(opponentName);
        labelTimeMy.setText(">CLOCK<");
        labelTimeOp.setText(">CLOCK<");
        chessPanel.revalidate();
        switchLayout(cards[3]);
    }

    private void exitGameEvent() {
        serverHandler.askToResign();
        switchLayout(cards[1]);
    }

    private void goBackEvent(){
        switchLayout(cards[1]);
    }

    private void gotListUpdate(Vector<DatumGame> g) {
        updateGameList(g);
    }

    private void initMenuPanel() {
        String defaultPath = "src/main/sprites/";
        try {
            menuPanel = new TexturePanel();
            menuPanel.setTexture(new TexturePaint(ImageIO.read(new File(defaultPath+"tile.jpg")),new Rectangle(N, N)));
        } catch (IOException e) {
            e.printStackTrace();
        }
        menuPanel.setLayout(new GridBagLayout());
        JPanel menuForm = new JPanel(new GridLayout(3, 1));
        menuForm.setBorder(BorderFactory.createLineBorder(Color.black));

        Font font1 = new Font("Arial", Font.PLAIN, 22);

        JPanel loginPanel = new JPanel();
        loginPanel.setLayout(new BorderLayout());
        loginField = new JTextArea();
        loginField.setFont(font1);
        loginPanel.add(new JLabel("Login"), BorderLayout.NORTH);
        loginPanel.add(loginField, BorderLayout.SOUTH);

        JPanel passwordPanel = new JPanel();
        passwordPanel.setLayout(new BorderLayout());
        passwordField = new JPasswordField();
        passwordField.setFont(font1);
        passwordPanel.add(new JLabel("Password"), BorderLayout.NORTH);
        passwordPanel.add(passwordField, BorderLayout.SOUTH);

        JButton submitButton = new JButton("Log in / Sign up");

        menuForm.add(loginPanel);
        menuForm.add(passwordPanel);
        menuForm.add(submitButton);

        menuPanel.add(menuForm);

        submitButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                loginEvent();
            }
        });

        mainPanel.add(menuPanel, cards[0]);
    }

    private void initChessboard(){
        chessPanel=new JPanel();
        chessPanel.setLayout(new BorderLayout());
        chessPanel.setBackground(Color.YELLOW);
        chessBoard = new ChessBoardPane(true);
        chessPanel.add(chessBoard,BorderLayout.CENTER);

        chessSidePanel=new JPanel();
        chessSidePanel.setLayout(new GridLayout(5,1));
        chessPanel.add(chessSidePanel,BorderLayout.EAST);
        labelMy = new JLabel();
        labelOp = new JLabel();
        buttonExit = new JButton("EXIT");
        labelTimeMy = new JLabel();
        labelTimeOp = new JLabel();

        chessSidePanel.add(labelOp);
        chessSidePanel.add(labelTimeOp);
        chessSidePanel.add(buttonExit);
        chessSidePanel.add(labelTimeMy);
        chessSidePanel.add(labelMy);

        chessPanel.revalidate();

        buttonExit.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                exitGameEvent();
            }
        });

        mainPanel.add(chessPanel, cards[3]);
    }

    private void initResultsPanel(){
        resultsPanel=new JPanel(new GridBagLayout());
        resultsPanel.setBackground(Color.GRAY);
        resultsLabel = new JLabel();
        resultsExitButton = new JButton("Go back to menu");

        resultsPanel.add(resultsLabel);
        resultsPanel.add(resultsExitButton);

        resultsExitButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                goBackEvent();
            }
        });

        mainPanel.add(resultsPanel,cards[6]);
    }

    private void initListPanel() { //DatumUser[] availableGames
        listPanel = new JPanel(new BorderLayout());
        listPanel.setBackground(Color.BLUE);

        listPanelRightBar = new ListPanelRightSideBar();
        listPanelRightBar.setBackground(Color.LIGHT_GRAY);

        listPanelGameListSection = new JPanel(new GridLayout(1, 1));
        listPanelGameListSection.setBackground(Color.DARK_GRAY);
        listPanelGameListSection.setBorder(new EmptyBorder(10, 10, 10, 10));

        availableGamesPanel = new DataGamePanel(15);
        availableGames = new Vector<DatumGame>();

        JScrollPane scrollPane1 = new JScrollPane(availableGamesPanel);
        scrollPane1.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        listPanelGameListSection.add(scrollPane1);

        listPanel.add(listPanelRightBar, BorderLayout.EAST);
        listPanel.add(listPanelGameListSection, BorderLayout.CENTER);


        mainPanel.add(listPanel, cards[1]);
    }

    private void initLeaderboardPanel() {
        topUsers = new Vector<>();

        leaderboardsPanel = new JPanel(new BorderLayout());
        leaderboardsPanel.setBackground(Color.LIGHT_GRAY);

        leaderboardsPanelRightBar = new LeaderPanelRightSideBar();
        leaderboardsPanelRightBar.setBackground(Color.LIGHT_GRAY);

        topPanel = new JPanel(new GridLayout(1, 1));
        topPanel.setBackground(Color.DARK_GRAY);
        topPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        topUsersPanel = new DataUserPanel(15);

        JScrollPane scrollPane2 = new JScrollPane(topUsersPanel);
        scrollPane2.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        topPanel.add(scrollPane2);

        leaderboardsPanel.add(leaderboardsPanelRightBar, BorderLayout.EAST);
        leaderboardsPanel.add(topPanel, BorderLayout.CENTER);

        mainPanel.add(leaderboardsPanel, cards[2]);
    }

    private void initCreationPanel() {
        creationPanel = new JPanel(new GridBagLayout());

        creationPanel.setBackground(Color.GRAY);
        JPanel creationPanelButtonPanel = new JPanel(new GridLayout(2, 2));
        creationPanelButtonPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        JButton createButton = new JButton("Create");
        JButton cancelButton = new JButton("Cancel");
        Vector<String> data1 = new Vector<String>();
        for (int i = 0; i < timeTotalArray.length; i++)
            data1.add(String.format("%d min", timeTotalArray[i]));
        JComboBox<String> timeTotalCombobox = new JComboBox<String>(data1);
        Vector<String> data2 = new Vector<String>();
        for (int i = 0; i < timeIncrementArray.length; i++)
            data2.add(String.format("%d sec", timeIncrementArray[i]));
        JComboBox<String> timeIncrementCombobox = new JComboBox<String>(data2);

        creationPanelButtonPanel.add(timeTotalCombobox);
        creationPanelButtonPanel.add(timeIncrementCombobox);
        creationPanelButtonPanel.add(createButton);
        creationPanelButtonPanel.add(cancelButton);

        createButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                createGameEvent(timeTotalArray[timeTotalCombobox.getSelectedIndex()], timeIncrementArray[timeIncrementCombobox.getSelectedIndex()]);
                }
        });

        cancelButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                listEvent();
            }
        });

        creationPanel.add(creationPanelButtonPanel);
        mainPanel.add(creationPanel, cards[4]);
    }

    private void initWaitingPanel() {
        waitingPanel = new JPanel(new GridBagLayout());
        waitingPanel.setBackground(Color.GRAY);
        JPanel waitingPanelCenter = new JPanel(new BorderLayout());
        JLabel waitingLabel = new JLabel("Waiting for opponent...");
        JButton cancelButton = new JButton("Cancel");
        waitingPanelCenter.add(waitingLabel, BorderLayout.CENTER);
        waitingPanelCenter.add(cancelButton, BorderLayout.SOUTH);


        cancelButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                serverHandler.askToCancelCreateGame();
                listEvent();
            }
        });

        waitingPanel.add(waitingPanelCenter);
        mainPanel.add(waitingPanel, cards[5]);
    }

    private void updateUsersList(Vector<DatumUser> topUsers) {
        topUsersPanel.removeAll();
        for (int i = 0; i < topUsers.size(); i++) {
            String name = topUsers.elementAt(i).getName();
            float elo = topUsers.elementAt(i).getElo();
            int wins = topUsers.elementAt(i).getWins();
            int loses = topUsers.elementAt(i).getLoses();
            DatumUser datum = new DatumUser(name, wins, loses, elo);
            topUsersPanel.addDatum(datum);
        }
        topUsersPanel.revalidate();
        topUsersPanel.repaint();
    }

    private void updateGameList(Vector<DatumGame> availableGames) {
        availableGamesPanel.removeAll();
        for (int i = 0; i < availableGames.size(); i++) {
            String name = availableGames.elementAt(i).getName();
            int timeMin = availableGames.elementAt(i).getTimeControlMin();
            int timeSec = availableGames.elementAt(i).getTimeControlIncrement();
            float elo = availableGames.elementAt(i).getElo();
            DatumGame datum = new DatumGame(name, timeMin, timeSec, elo);
            availableGamesPanel.addDatum(datum);
        }
        availableGamesPanel.revalidate();
        availableGamesPanel.repaint();
    }

    private void switchLayout(String cardName) {
        CardLayout cl = (CardLayout) (mainPanel.getLayout());
        cl.show(mainPanel, cardName);
        revalidate();
        repaint();
        mainPanel.revalidate();
        mainPanel.repaint();
    }

    private void mainWindowPlacement() {
        Dimension windowSize = new Dimension(xWindowSize, yWindowSize);
        setPreferredSize(windowSize);

        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        Point middle = new Point(screenSize.width / 2, screenSize.height / 2);
        Point newWindowLocation = new Point(middle.x - getPreferredSize().width / 2, middle.y - getPreferredSize().height / 2);
        setLocation(newWindowLocation);
    }

    @SuppressWarnings("serial")
    static class DatumUserPanel extends JPanel {
        private static final int GBC_I = 3;
        private static int amount = 0;
        private DatumUser datum;
        private JLabel nameLabel = new JLabel();
        private JLabel winsLabel = new JLabel();
        private JLabel losesLabel = new JLabel();
        private JLabel eloLabel = new JLabel();

        public DatumUserPanel() {
            amount += 1;
            if (amount % 2 == 0) {
                this.setBackground(Color.LIGHT_GRAY);
            } else {
                this.setBackground(Color.WHITE);
            }
            setLayout(new GridLayout(1, 5));
            add(nameLabel);
            add(winsLabel);
            add(losesLabel);
            add(eloLabel);
        }

        public DatumUserPanel(DatumUser datum) {
            this();
            setDatumUser(datum);
        }

        public final void setDatumUser(DatumUser datum) {
            this.datum = datum;
            nameLabel.setText(datum.getName());
            winsLabel.setText("" + datum.getWins());
            losesLabel.setText("" + datum.getLoses());
            eloLabel.setText("" + ((float) ((int) (datum.getElo() * 100.0))) / 100.0);
        }

        public DatumUser getDatum() {
            return datum;
        }

        private GridBagConstraints createGbc(int x, int y) {
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.gridx = x;
            gbc.gridy = y;
            gbc.insets = new Insets(GBC_I, GBC_I, GBC_I, GBC_I);
            gbc.insets.left = x != 0 ? 3 * GBC_I : GBC_I;
            gbc.weightx = 0.0;
            gbc.weighty = 0.0;
            return gbc;
        }
    }

    private class ListPanelRightSideBar extends JPanel {
        private JLabel userName;
        private JLabel win;
        private JLabel lose;
        private JLabel elo;
        private JLabel place;
        private JButton leaderboardsButton;
        private JButton newGameButton;
        ListPanelRightSideBar() {
            setLayout(new GridLayout(7, 1));
            userName = new JLabel("username");
            win = new JLabel("win");
            lose = new JLabel("lose");
            elo = new JLabel("elo");
            place = new JLabel("place");
            leaderboardsButton = new JButton("LeaderBoards");
            newGameButton = new JButton("New Game");
            setBorder(new EmptyBorder(5, 5, 5, 5));

            add(userName);
            add(win);
            add(lose);
            add(elo);
            add(place);
            add(leaderboardsButton);
            add(newGameButton);

            newGameButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    createSettingsEvent();
                }
            });

            leaderboardsButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    leaderboardEvent();
                }
            });


        }

        public void updateInfo() {
            userName.setText("Username: "+myUsername);
            win.setText("Wins: "+myWins);
            lose.setText("Loses: "+myLoses);
            elo.setText(""+myElo+" elo");
            place.setText("");
        }
    }

    private class LeaderPanelRightSideBar extends JPanel {
        private JLabel userName;
        private JLabel win;
        private JLabel lose;
        private JLabel elo;
        private JLabel place;
        private JButton backButton;

        LeaderPanelRightSideBar() {
            setLayout(new GridLayout(7, 1));
            userName = new JLabel("username");
            win = new JLabel("win");
            lose = new JLabel("lose");
            elo = new JLabel("elo");
            place = new JLabel("place");
            backButton = new JButton("Back");
            setBorder(new EmptyBorder(5, 5, 5, 5));

            add(userName);
            add(win);
            add(lose);
            add(elo);
            add(place);
            add(backButton);

            backButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    listEvent();
                }
            });


        }

        public void updateInfo() {
            userName.setText("Username: "+myUsername);
            win.setText("Wins: "+myWins);
            lose.setText("Loses: "+myLoses);
            elo.setText(""+myElo+" elo");
            place.setText("");
        }

        public void update() {
        }
    }

    class PackageHandler extends Thread {
        public static final int TIMEOUT = 10000;
        Socket serverSocket;
        DataInputStream inputStream;
        DataOutputStream outputStream;
        boolean onConnection;

        PackageHandler(Socket _s, DataInputStream _i, DataOutputStream _o) {
            serverSocket = _s;
            inputStream = _i;
            outputStream = _o;
            onConnection = true;
        }

        @Override
        public void run() {
            int type;
            while (onConnection) {
                try {
                    type = inputStream.readInt();
                    switch (type) {
                        case RequestType.PING_TYPE:
                            synchronized (outputStream) {
                                outputStream.writeInt(RequestType.PING_TYPE);
                                outputStream.flush();
                            }
                            break;

                        case RequestType.LEADERBOARDS:
                            int n = 0;
                            synchronized (inputStream){
                                topUsers.clear();
                                n = inputStream.readInt();
                                for(int i = 0; i<n;i++){
                                    String _u = inputStream.readUTF();
                                    float _e= inputStream.readFloat();
                                    int _w=inputStream.readInt();
                                    int _l= inputStream.readInt();
                                    topUsers.add(new DatumUser(_u,_w,_l,_e));
                                }
                            }
                            leaderboardsPanelRightBar.updateInfo();
                            updateUsersList(topUsers);
                            break;

                        case RequestType.GAME_START:
                            synchronized (inputStream){
                                opponentName=inputStream.readUTF();
                                gameStartEvent();
                            }
                            break;

                        case RequestType.GAME_TYPE:
                            String move ="";
                            int timeCreator=0;
                            int timeOpponent=0;
                            synchronized (inputStream){
                                move = inputStream.readUTF(); System.out.println(move);
                                yourTurn=inputStream.readBoolean(); System.out.println(yourTurn);
                                yourTurn=!youCreator && !yourTurn || youCreator && yourTurn;
                                timeCreator=inputStream.readInt(); System.out.println(timeCreator);
                                timeOpponent=inputStream.readInt(); System.out.println(timeOpponent);
                                System.out.println("-----");
                                System.out.println("Move:"+move);
                                System.out.println("My turn:"+yourTurn);
                                System.out.println("TimeCreator:"+timeCreator);
                                System.out.println("TimeOpponent:"+timeCreator);
                                System.out.println("-----");
                                if(youCreator){
                                    myTime=timeCreator;
                                    opponentTime=timeOpponent;
                                }
                                else{
                                    myTime=timeOpponent;
                                    opponentTime=timeCreator;
                                }
                                labelTimeMy.setText(""+myTime/60+":"+myTime%60);
                                labelTimeOp.setText(""+opponentTime/60+":"+opponentTime%60);
                                if(yourTurn){
                                    yourTurn=false;
                                    chessBoard.makeMove(new Move(move, Side.WHITE));
                                    chessBoard.updateBoard();
                                    yourTurn=true;
                                }
                                if(countingTimer!=null){
                                    countingTimer.stop();
                                }
                                if(chessBoard.board.getHistory().size()>2){
                                    if(yourTurn){
                                        countingTimer = new Timer(950, new ActionListener() {
                                            @Override
                                            public void actionPerformed(ActionEvent e) {
                                                myTime-=1;
                                                labelTimeMy.setText(""+myTime/60+":"+myTime%60);
                                                if(myTime<1){
                                                    serverHandler.askToCheckTime(youCreator);
                                                }
                                            }
                                        });
                                        countingTimer.start();
                                    }
                                    else{
                                        countingTimer = new Timer(950, new ActionListener() {
                                            @Override
                                            public void actionPerformed(ActionEvent e) {
                                                opponentTime-=1;
                                                labelTimeOp.setText(""+opponentTime/60+":"+opponentTime%60);
                                                if(opponentTime<1){
                                                    serverHandler.askToCheckTime(youCreator);
                                                }
                                            }
                                        });
                                        countingTimer.start();
                                    }
                                }
                            }
                            break;

                        case RequestType.GAME_RESULTS:
                            boolean draw;
                            boolean white;
                            synchronized(inputStream){
                                draw=inputStream.readBoolean();
                                white=inputStream.readBoolean();
                            }
                            endGameEvent(draw,white);
                            break;

                        case RequestType.GAME_LIST:
                            synchronized (inputStream) {
                                myUsername = inputStream.readUTF();
                                myElo = inputStream.readFloat();
                                myWins = inputStream.readInt();
                                myLoses = inputStream.readInt();
                                int amount = inputStream.readInt();
                                System.out.println("Games amount:" + amount);
                                availableGames.clear();
                                for (int i = 0; i < amount; i++) {
                                    String nameCreator = inputStream.readUTF();
                                    int timeControlTotal = inputStream.readInt();
                                    int timeControlIncrement = inputStream.readInt();
                                    float eloCreator = inputStream.readFloat();
                                    availableGames.add(new DatumGame(nameCreator, timeControlTotal, timeControlIncrement, eloCreator));
                                }
                                listPanelRightBar.updateInfo();
                            }
                            gotListUpdate(availableGames);
                            break;

                        case RequestType.GAME_CREATE:
                            break;

                        default:
                            System.out.println("Type is " + type + ". Bad request type.");
                            onConnection = false;
                    }
                } catch (java.net.SocketTimeoutException e) {
                    System.out.println("No response. Disconnected.");
                    onConnection = false;
                } catch (java.io.EOFException e) {
                    System.out.println("Server closed stream. Disconnected.");
                    onConnection = false;
                } catch (Exception e) {
                    System.out.println("Unknown error occurred");
                    System.out.println(e);
                    onConnection = false;
                }
            }
        }

        public void askToCreateGame(int total, int increment) {
            synchronized (outputStream) {
                try {
                    outputStream.writeInt(RequestType.GAME_CREATE);
                    outputStream.writeInt(total);
                    outputStream.writeInt(increment);
                    outputStream.flush();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        public void askToCancelCreateGame() {
            synchronized (outputStream) {
                try {
                    outputStream.writeInt(RequestType.GAME_CANCEL_WAITING);
                    outputStream.flush();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        public void askToCheckTime(boolean creator){
            synchronized (outputStream) {
                try {
                    outputStream.writeInt(RequestType.GAME_TIMEOUT);
                    outputStream.writeBoolean(creator);
                    outputStream.flush();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        public void askToShowLeaderBoards(){
            synchronized (outputStream) {
                try {
                    outputStream.writeInt(RequestType.LEADERBOARDS);
                    outputStream.flush();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        public void askToJoinGame(String name){
            synchronized(outputStream){
                try {
                    outputStream.writeInt(RequestType.GAME_JOIN);
                    outputStream.writeUTF(name);
                    outputStream.flush();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            return;
        }

        public void askToResign(){
            synchronized (outputStream){
                try {
                    outputStream.writeInt(RequestType.GAME_RESIGN);
                    outputStream.flush();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        public void sendMove(Move move){
            String stringMove = move.toString();
            synchronized (outputStream){
                try {
                    outputStream.writeInt(RequestType.GAME_TYPE);
                    outputStream.writeUTF(stringMove);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @SuppressWarnings("serial")
    class DatumUserRenderer extends DatumUserPanel implements ListCellRenderer<DatumUser> {

        @Override
        public Component getListCellRendererComponent(JList<? extends DatumUser> list, DatumUser value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            setDatumUser(value);
            return this;
        }

    }

    @SuppressWarnings("serial")
    class DataUserPanel extends JPanel implements Scrollable {
        private int visibleRowCount = 1;

        public DataUserPanel(int visibleRowCount) {
            this.visibleRowCount = visibleRowCount;
            setLayout(new GridLayout(0, 1));
        }

        public void addDatum(DatumUser datum) {
            add(new DatumUserPanel(datum));
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            if (getComponentCount() > 0) {
                JComponent comp = (JComponent) getComponents()[0];
                int width = getPreferredSize().width;
                int height = visibleRowCount * comp.getPreferredSize().height;
                Dimension d = new Dimension(width, height);
                return d;
            } else {
                return new Dimension(0, 0);
            }
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            if (getComponentCount() > 0) {
                JComponent comp = (JComponent) getComponents()[0];
                Dimension d = comp.getPreferredSize();
                if (orientation == SwingConstants.VERTICAL) {
                    return d.height;
                } else {
                    return d.width;
                }
            }
            return 0;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            if (getComponentCount() > 0) {
                JComponent comp = (JComponent) getComponents()[0];
                Dimension d = comp.getPreferredSize();
                if (orientation == SwingConstants.VERTICAL) {
                    return visibleRowCount * d.height;
                } else {
                    return d.width;
                }
            }
            return 0;
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }

    }

    class DatumUser {
        private String name;
        private int wins;
        private int loses;
        private float elo;

        public DatumUser(String _n, int _w, int _l, float _e) {
            this.name = _n;
            this.wins = _w;
            this.loses = _l;
            this.elo = _e;
        }

        public String getName() {
            return name;
        }

        public int getWins() {
            return wins;
        }

        public int getLoses() {
            return loses;
        }

        public float getElo() {
            return elo;
        }


    }


//----------------------------

    @SuppressWarnings("serial")
    class DatumGameRenderer extends DatumGamePanel implements ListCellRenderer<DatumGame> {

        @Override
        public Component getListCellRendererComponent(JList<? extends DatumGame> list, DatumGame value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            setDatumGame(value);
            return this;
        }

    }

    @SuppressWarnings("serial")
    class DataGamePanel extends JPanel implements Scrollable {
        private int visibleRowCount = 1;

        public DataGamePanel(int visibleRowCount) {
            this.visibleRowCount = visibleRowCount;
            setLayout(new GridLayout(0, 1));
        }

        public void addDatum(DatumGame datum) {
            add(new DatumGamePanel(datum));
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            if (getComponentCount() > 0) {
                JComponent comp = (JComponent) getComponents()[0];
                int width = getPreferredSize().width;
                int height = visibleRowCount * comp.getPreferredSize().height;
                Dimension d = new Dimension(width, height);
                return d;
            } else {
                return new Dimension(0, 0);
            }
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            if (getComponentCount() > 0) {
                JComponent comp = (JComponent) getComponents()[0];
                Dimension d = comp.getPreferredSize();
                if (orientation == SwingConstants.VERTICAL) {
                    return d.height;
                } else {
                    return d.width;
                }
            }
            return 0;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            if (getComponentCount() > 0) {
                JComponent comp = (JComponent) getComponents()[0];
                Dimension d = comp.getPreferredSize();
                if (orientation == SwingConstants.VERTICAL) {
                    return visibleRowCount * d.height;
                } else {
                    return d.width;
                }
            }
            return 0;
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }

    }

    @SuppressWarnings("serial")
    class DatumGamePanel extends JPanel {
        private static final int GBC_I = 3;
        private DatumGame datum;
        private JLabel nameLabel = new JLabel();
        private JLabel timeLabel = new JLabel();
        private JLabel eloLabel = new JLabel();
        private JButton button = new JButton("JOIN");

        public DatumGamePanel() {
            setLayout(new GridLayout(1, 5));
            add(nameLabel);
            add(timeLabel);
            add(eloLabel);
            add(button);
            button.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    joinEvent(nameLabel.getText(),Float.parseFloat(eloLabel.getText()));
                }
            });
        }

        public DatumGamePanel(DatumGame datum) {
            this();
            setDatumGame(datum);
        }

        public final void setDatumGame(DatumGame datum) {
            this.datum = datum;
            nameLabel.setText(datum.getName());
            timeLabel.setText("" + datum.getTimeControlMin() + "|" + datum.getTimeControlIncrement());
            eloLabel.setText("" + ((float) ((int) (datum.getElo() * 100.0))) / 100.0);
        }

        public DatumGame getDatum() {
            return datum;
        }

        private GridBagConstraints createGbc(int x, int y) {
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.gridx = x;
            gbc.gridy = y;
            gbc.insets = new Insets(GBC_I, GBC_I, GBC_I, GBC_I);
            gbc.insets.left = x != 0 ? 3 * GBC_I : GBC_I;
            gbc.weightx = 0.0;
            gbc.weighty = 0.0;
            return gbc;
        }
    }

    class DatumGame {
        private String name;
        private int timeControlTotal;
        private int timeControlIncrement;
        private float elo;

        public DatumGame(String _n, int _tm, int _ts, float _e) {
            this.name = _n;
            this.timeControlTotal = _tm;
            this.timeControlIncrement = _ts;
            this.elo = _e;
        }

        public String getName() {
            return name;
        }

        public int getTimeControlMin() {
            return timeControlTotal;
        }


        public int getTimeControlIncrement() {
            return timeControlIncrement;
        }

        public float getElo() {
            return elo;
        }
    }


    public class ChessBoardPane extends JPanel {
        Board board;
        BufferedImage[] imageCollection;
        Vector<Cell> cells;
        void initImages(){
            String defaultPath = "src/main/sprites/";
            imageCollection = new BufferedImage[12];
            try {
                imageCollection[0]=ImageIO.read(new File(defaultPath+"wP.png"));
                imageCollection[6]=ImageIO.read(new File(defaultPath+"bP.png"));
                imageCollection[1]=ImageIO.read(new File(defaultPath+"wN.png"));
                imageCollection[7]=ImageIO.read(new File(defaultPath+"bN.png"));
                imageCollection[2]=ImageIO.read(new File(defaultPath+"wB.png"));
                imageCollection[8]=ImageIO.read(new File(defaultPath+"bB.png"));
                imageCollection[3]=ImageIO.read(new File(defaultPath+"wR.png"));
                imageCollection[9]=ImageIO.read(new File(defaultPath+"bR.png"));
                imageCollection[4]=ImageIO.read(new File(defaultPath+"wQ.png"));
                imageCollection[10]=ImageIO.read(new File(defaultPath+"bQ.png"));
                imageCollection[5]=ImageIO.read(new File(defaultPath+"wK.png"));
                imageCollection[11]=ImageIO.read(new File(defaultPath+"bK.png"));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        boolean reversed = true;

        int source = -1;
        int destination = -1;
        boolean ended = false;


        public ChessBoardPane(boolean isWhite) {
            initImages();
            board = new Board();
            cells = new Vector<>();
            reversed=!isWhite;
            int index = 0;
            setLayout(new ChessBoardLayoutManager());
            if(reversed){
                for (int row = 0; row < 8; row++) {
                    for (int col = 0; col < 8; col++) {
                        Color color = index % 2 == 1 ? Color.BLACK : Color.WHITE;
                        Cell e =new ChessBoardPane.Cell(row*8+col);
                        add(e, new Point(7-col, row));
                        cells.add(e);
                        index++;
                    }
                    index++;
                }
            }
            else{
                for (int row = 0; row < 8; row++) {
                    for (int col = 0; col < 8; col++) {
                        Color color = index % 2 == 1 ? Color.BLACK : Color.WHITE;
                        Cell e =new ChessBoardPane.Cell(row*8+col);
                        add(e, new Point(col, 7-row));
                        cells.add(e);
                        index++;
                    }
                    index++;
                }
            }

            addComponentListener(new ComponentListener() {
                @Override
                public void componentResized(ComponentEvent e) {
                    updateBoard();
                }

                @Override
                public void componentMoved(ComponentEvent e) {

                }

                @Override
                public void componentShown(ComponentEvent e) {

                }

                @Override
                public void componentHidden(ComponentEvent e) {

                }
            });
        }

        public class Cell extends JPanel {
            final Color WHITE = new Color(245,245,220);
            final Color BLACK = new Color(107,142,35);
            int id;
            JLabel filling = new JLabel();
            public Cell(int _id) {
                setLayout(new GridBagLayout());
                id = _id;
                setOpaque(true);
                assignCellColor();
                updateFilling();
                add(filling);
                addMouseListener(new MouseListener() {
                    @Override
                    public void mouseClicked(MouseEvent e) {
                    }

                    @Override
                    public void mousePressed(MouseEvent e) {
                        createMove();
                    }

                    @Override
                    public void mouseReleased(MouseEvent e) {

                    }

                    @Override
                    public void mouseEntered(MouseEvent e) {

                    }

                    @Override
                    public void mouseExited(MouseEvent e) {

                    }
                });
            }
            public void assignCellColor(){
                int r = id/8;
                int f = id%8;
                if((r+f)%2==0){
                    setBackground(BLACK);
                }
                else{
                    setBackground(WHITE);
                }
            }

            public void updateFilling(){
                if(!board.getPiece(Square.values()[id]).equals(Piece.NONE)){
                    int sideSize=50;
                    if(this.getHeight()>0){
                        sideSize=this.getHeight();
                    }
                    Image dimg = imageCollection[board.getPiece(Square.squareAt(id)).ordinal()].getScaledInstance(sideSize,sideSize,
                            Image.SCALE_SMOOTH);
                    ImageIcon imageIcon = new ImageIcon(dimg);
                    filling.setIcon(imageIcon);
                    filling.revalidate();
                }
                else{
                    filling.setIcon(null);
                    filling.revalidate();
                }
            }

            public void createMove(){
                if(yourTurn){
                    if(source==-1){
                        source = id;
                        if(board.getPiece(Square.values()[source]).equals(Piece.NONE)){
                            source=-1;
                        }
                    }
                    else{
                        destination = id;
                        Move currentMove = new Move(Square.values()[source],Square.values()[destination]);
                        if (board.legalMoves().contains(currentMove)){
                            makeMove(currentMove);
                            serverHandler.sendMove(currentMove);
                        }
                        else{
                            //System.out.println("ILLEGAL");
                        }
                        source = -1;
                        destination = -1;
                    }
                }
            }

            @Override
            public Dimension getPreferredSize() {
                return new Dimension(25, 25);
            }
        }

        public void makeMove(Move currentMove){
            board.doMove(currentMove);
            updateBoard();
            yourTurn=false;
            if(board.isMated() || board.isDraw()){
                System.out.println("GAME END");
                System.out.println("TOTAL MOVES: "+board.getHistory().size());
                if(board.isDraw()){
                    System.out.println("DRAW");
                }
                else{
                    if(board.getHistory().size()%2==0){
                        System.out.println("WHITE WINS");
                    }
                    else{
                        System.out.println("BLACK WINS");
                    }
                }
            }

        }
        public void updateBoard(){
            for (Cell e:cells) {
                e.updateFilling();
            }
        }

        public class ChessBoardLayoutManager implements LayoutManager2 {

            private Map<Point, Component> mapComps;

            public ChessBoardLayoutManager() {
                mapComps = new HashMap<>(25);
            }

            @Override
            public void addLayoutComponent(Component comp, Object constraints) {
                if (constraints instanceof Point) {

                    mapComps.put((Point) constraints, comp);

                } else {

                    throw new IllegalArgumentException("ChessBoard constraints must be a Point");

                }
            }

            @Override
            public Dimension maximumLayoutSize(Container target) {
                return preferredLayoutSize(target);
            }

            @Override
            public float getLayoutAlignmentX(Container target) {
                return 0.5f;
            }

            @Override
            public float getLayoutAlignmentY(Container target) {
                return 0.5f;
            }

            @Override
            public void invalidateLayout(Container target) {
            }

            @Override
            public void addLayoutComponent(String name, Component comp) {
            }

            @Override
            public void removeLayoutComponent(Component comp) {
                Point[] keys = mapComps.keySet().toArray(new Point[mapComps.size()]);
                for (Point p : keys) {
                    if (mapComps.get(p).equals(comp)) {
                        mapComps.remove(p);
                        break;
                    }
                }
            }

            @Override
            public Dimension preferredLayoutSize(Container parent) {
                return new CellGrid(mapComps).getPreferredSize();
            }

            @Override
            public Dimension minimumLayoutSize(Container parent) {
                return preferredLayoutSize(parent);
            }

            @Override
            public void layoutContainer(Container parent) {
                int width = parent.getWidth();
                int height = parent.getHeight();

                int gridSize = Math.min(width, height);

                CellGrid grid = new CellGrid(mapComps);
                int rowCount = grid.getRowCount();
                int columnCount = grid.getColumnCount();

                int cellSize = gridSize / Math.max(rowCount, columnCount);

                int xOffset = (width - (cellSize * columnCount)) / 2;
                int yOffset = (height - (cellSize * rowCount)) / 2;

                Map<Integer, java.util.List<CellGrid.Cell>> cellRows = grid.getCellRows();
                for (Integer row : cellRows.keySet()) {
                    java.util.List<CellGrid.Cell> rows = cellRows.get(row);
                    for (CellGrid.Cell cell : rows) {
                        Point p = cell.getPoint();
                        Component comp = cell.getComponent();

                        int x = xOffset + (p.x * cellSize);
                        int y = yOffset + (p.y * cellSize);

                        comp.setLocation(x, y);
                        comp.setSize(cellSize, cellSize);

                    }
                }

            }

            public class CellGrid {

                private Dimension prefSize;
                private int cellWidth;
                private int cellHeight;

                private Map<Integer, java.util.List<Cell>> mapRows;
                private Map<Integer, java.util.List<Cell>> mapCols;

                public CellGrid(Map<Point, Component> mapComps) {
                    mapRows = new HashMap<>(25);
                    mapCols = new HashMap<>(25);
                    for (Point p : mapComps.keySet()) {
                        int row = p.y;
                        int col = p.x;
                        java.util.List<Cell> rows = mapRows.get(row);
                        java.util.List<Cell> cols = mapCols.get(col);
                        if (rows == null) {
                            rows = new ArrayList<>(25);
                            mapRows.put(row, rows);
                        }
                        if (cols == null) {
                            cols = new ArrayList<>(25);
                            mapCols.put(col, cols);
                        }
                        Cell cell = new Cell(p, mapComps.get(p));
                        rows.add(cell);
                        cols.add(cell);
                    }

                    int rowCount = mapRows.size();
                    int colCount = mapCols.size();

                    cellWidth = 0;
                    cellHeight = 0;

                    for (java.util.List<Cell> comps : mapRows.values()) {
                        for (Cell cell : comps) {
                            Component comp = cell.getComponent();
                            cellWidth = Math.max(cellWidth, comp.getPreferredSize().width);
                            cellHeight = Math.max(cellHeight, comp.getPreferredSize().height);
                        }
                    }

                    int cellSize = Math.max(cellHeight, cellWidth);

                    prefSize = new Dimension(cellSize * colCount, cellSize * rowCount);
                }

                public int getRowCount() {
                    return getCellRows().size();
                }

                public int getColumnCount() {
                    return getCellColumns().size();
                }

                public Map<Integer, java.util.List<Cell>> getCellColumns() {
                    return mapCols;
                }

                public Map<Integer, List<Cell>> getCellRows() {
                    return mapRows;
                }

                public Dimension getPreferredSize() {
                    return prefSize;
                }

                public int getCellHeight() {
                    return cellHeight;
                }

                public int getCellWidth() {
                    return cellWidth;
                }

                public class Cell {

                    private Point point;
                    private Component component;

                    public Cell(Point p, Component comp) {
                        this.point = p;
                        this.component = comp;
                    }

                    public Point getPoint() {
                        return point;
                    }

                    public Component getComponent() {
                        return component;
                    }

                }

            }
        }
    }

    static int N = 128;

    private class TexturePanel extends JPanel {

        private TexturePaint paint;

        public void setTexture(TexturePaint tp) {
            this.paint = tp;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2d = (Graphics2D) g;
            g2d.setPaint(paint);
            g2d.fillRect(0, 0, getWidth(), getHeight());
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(N, N);
        }
    }
}

