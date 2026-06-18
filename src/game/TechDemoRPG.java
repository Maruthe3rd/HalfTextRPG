package game;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.sql.*;

public class TechDemoRPG {

    private JFrame frame;
    private JTextArea displayArea;
    private JButton btnAction1;
    private JButton btnAction2;
    private JPanel buttonPanel;

    private Connection conn;
    private int currentNode = 1;

    private boolean inCombat = false;
    private int playerHP = 20;
    private int enemyHP = 15;
    private boolean waitingForReaction = false;
    private Timer reactionTimer;
    private Timer sequenceTimer;
    private JPanel Panel;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new TechDemoRPG().start());
    }

    public void start() {
        initDatabase();
        initGUI();
        loadStoryNode(1);
    }

    private void initDatabase() {
        try {
            conn = DriverManager.getConnection("jdbc:sqlite::memory:");
            Statement stmt = conn.createStatement();

            stmt.execute("CREATE TABLE StoryNodes (" +
                    "id INTEGER PRIMARY KEY, " +
                    "narration TEXT, " +
                    "btn1_text TEXT, btn1_target INTEGER, " +
                    "btn2_text TEXT, btn2_target INTEGER, " +
                    "is_combat BOOLEAN)");


            String insertSQL = "INSERT INTO StoryNodes VALUES (?, ?, ?, ?, ?, ?, ?)";
            PreparedStatement pstmt = conn.prepareStatement(insertSQL);

            // Node 1: The Choice
            pstmt.setInt(1, 1);
            pstmt.setString(2, "You stand before the Glitched Core. It hums with malice.");
            pstmt.setString(3, "Approach it");
            pstmt.setInt(4, 2);
            pstmt.setString(5, "Walk away");
            pstmt.setInt(6, 3);
            pstmt.setBoolean(7, false);
            pstmt.executeUpdate();

            // Node 2: Combat Trigger
            pstmt.setInt(1, 2);
            pstmt.setString(2, "A shadow detaches from the Core! It attacks!");
            pstmt.setString(3, "Fight"); pstmt.setInt(4, -1); // -1 is handled dynamically
            pstmt.setString(5, "Flee"); pstmt.setInt(6, 3);
            pstmt.setBoolean(7, true);
            pstmt.executeUpdate();

            // Node 3: Bad End
            pstmt.setInt(1, 3);
            pstmt.setString(2, "You walked away. The world was consumed by the glitch.\n\n*** BAD END ***");
            pstmt.setString(3, "Restart"); pstmt.setInt(4, 1);
            pstmt.setString(5, "Exit"); pstmt.setInt(6, 0);
            pstmt.setBoolean(7, false);
            pstmt.executeUpdate();

            // Node 4: Good End (Triggered after combat win)
            pstmt.setInt(1, 4);
            pstmt.setString(2, "The shadow dissolves. You shattered the Core.\n\n*** GOOD END ***");
            pstmt.setString(3, "Restart"); pstmt.setInt(4, 1);
            pstmt.setString(5, "Exit"); pstmt.setInt(6, 0);
            pstmt.setBoolean(7, false);
            pstmt.executeUpdate();

            pstmt.close();
            stmt.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void loadStoryNode(int id) {
        if (id == 0) System.exit(0);

        try {
            PreparedStatement pstmt = conn.prepareStatement("SELECT * FROM StoryNodes WHERE id = ?");
            pstmt.setInt(1, id);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                currentNode = id;
                displayArea.setText(rs.getString("narration"));

                btnAction1.setText(rs.getString("btn1_text"));
                btnAction2.setText(rs.getString("btn2_text"));

                boolean isCombat = rs.getBoolean("is_combat");

                int target1 = rs.getInt("btn1_target");
                int target2 = rs.getInt("btn2_target");

                for(ActionListener al : btnAction1.getActionListeners()) btnAction1.removeActionListener(al);
                for(ActionListener al : btnAction2.getActionListeners()) btnAction2.removeActionListener(al);

                if (isCombat) {
                    btnAction1.addActionListener(e -> startCombat());
                    btnAction2.addActionListener(e -> loadStoryNode(target2));
                } else {
                    btnAction1.addActionListener(e -> loadStoryNode(target1));
                    btnAction2.addActionListener(e -> loadStoryNode(target2));
                }
            }
            rs.close();
            pstmt.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }


    private void startCombat() {
        inCombat = true;
        playerHP = 20;
        enemyHP = 15;
        updateCombatUI("Combat Initiated!\nPlayer HP: " + playerHP + " | Enemy HP: " + enemyHP);

        for(ActionListener al : btnAction1.getActionListeners()) btnAction1.removeActionListener(al);
        for(ActionListener al : btnAction2.getActionListeners()) btnAction2.removeActionListener(al);

        btnAction1.setText("ATTACK");
        btnAction2.setText("DEFEND");

        btnAction1.addActionListener(e -> attemptAttack());
        btnAction2.addActionListener(e -> attemptDefend());

        enemyTurn();
    }

    private void enemyTurn() {
        if (!inCombat) return;
        btnAction1.setEnabled(false);
        btnAction2.setEnabled(false);

        updateCombatUI("Enemy is preparing to strike...");

        sequenceTimer = new Timer(1500, e -> {
            sequenceTimer.stop();
            updateCombatUI("!!! BLOCK NOW !!!");
            btnAction2.setEnabled(true);
            waitingForReaction = true;

            reactionTimer = new Timer(800, ev -> {
                reactionTimer.stop();
                if (waitingForReaction) {
                    // fail
                    waitingForReaction = false;
                    playerHP -= 5;
                    updateCombatUI("Too slow! You took 5 damage.\nPlayer HP: " + playerHP + " | Enemy HP: " + enemyHP);
                    checkDeath();
                    if(inCombat) TimerDelay(1500, this::playerTurn);
                }
            });
            reactionTimer.setRepeats(false);
            reactionTimer.start();
        });
        sequenceTimer.setRepeats(false);
        sequenceTimer.start();
    }

    private void attemptDefend() {
        if (waitingForReaction) {
            waitingForReaction = false;
            reactionTimer.stop();
            btnAction2.setEnabled(false);
            updateCombatUI("Perfect Block! 0 Damage taken.\nPlayer HP: " + playerHP + " | Enemy HP: " + enemyHP);
            TimerDelay(1500, this::playerTurn);
        }
    }

    private void playerTurn() {
        if (!inCombat) return;
        btnAction1.setEnabled(false);
        btnAction2.setEnabled(false);

        updateCombatUI("Your turn. Aiming...");

        sequenceTimer = new Timer(1500, e -> {
            sequenceTimer.stop();
            updateCombatUI("!!! STRIKE NOW !!!");
            btnAction1.setEnabled(true);
            waitingForReaction = true;

            reactionTimer = new Timer(800, ev -> {
                reactionTimer.stop();
                if (waitingForReaction) {
                    waitingForReaction = false;
                    btnAction1.setEnabled(false);
                    updateCombatUI("You missed the window. Glancing blow. 1 Damage.\nPlayer HP: " + playerHP + " | Enemy HP: " + enemyHP);
                    enemyHP -= 1;
                    checkDeath();
                    if(inCombat) TimerDelay(1500, this::enemyTurn);
                }
            });
            reactionTimer.setRepeats(false);
            reactionTimer.start();
        });
        sequenceTimer.setRepeats(false);
        sequenceTimer.start();
    }

    private void attemptAttack() {
        if (waitingForReaction) {
            waitingForReaction = false;
            reactionTimer.stop();
            btnAction1.setEnabled(false);
            enemyHP -= 8;
            updateCombatUI("CRITICAL HIT! 8 Damage!\nPlayer HP: " + playerHP + " | Enemy HP: " + enemyHP);
            checkDeath();
            if(inCombat) TimerDelay(1500, this::enemyTurn);
        }
    }

    private void checkDeath() {
        if (playerHP <= 0) {
            inCombat = false;
            loadStoryNode(3); // Bad end
        } else if (enemyHP <= 0) {
            inCombat = false;
            loadStoryNode(4); // Good end
        }
    }

    private void initGUI() {
        frame = new JFrame("Reactive RPG Tech Demo");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1920, 1080);
        frame.setLayout(new BorderLayout());
        frame.getContentPane().setBackground(Color.BLACK);

        displayArea = new JTextArea();
        displayArea.setEditable(false);
        displayArea.setBackground(Color.BLACK);
        displayArea.setForeground(Color.GREEN);
        displayArea.setFont(new Font("Consolas", Font.BOLD, 16));
        displayArea.setMargin(new Insets(20, 20, 20, 20));
        displayArea.setLineWrap(true);
        displayArea.setWrapStyleWord(true);
        frame.add(new JScrollPane(displayArea), BorderLayout.CENTER);

        buttonPanel = new JPanel();
        buttonPanel.setLayout(new GridLayout(1, 2, 10, 10));
        buttonPanel.setMinimumSize(new Dimension(1920, 350));
        buttonPanel.setBackground(Color.BLACK);
        buttonPanel.setForeground(Color.GREEN);
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        btnAction1 = createStyledButton("");
        btnAction2 = createStyledButton("");

        buttonPanel.add(btnAction1);
        buttonPanel.add(btnAction2);
        frame.add(buttonPanel, BorderLayout.SOUTH);

        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private JButton createStyledButton(String text) {
        JButton btn = new JButton(text);
        btn.setMinimumSize(new Dimension(750, 750));
        btn.setBackground(Color.BLACK);
        btn.setForeground(Color.WHITE);
        btn.setFont(new Font("Consolas", Font.BOLD, 18));
        btn.setFocusPainted(true);
        return btn;
    }

    private void updateCombatUI(String text) {
        displayArea.setText(text);
    }

    private void TimerDelay(int ms, Runnable action) {
        Timer t = new Timer(ms, e -> action.run());
        t.setRepeats(false);
        t.start();
    }
}

