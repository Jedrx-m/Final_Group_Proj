package Main; // package name

import javax.swing.*; // Swing UI components
import java.awt.*; // layouts, colors
import java.awt.event.ActionEvent; // menu actions
import java.awt.event.ActionListener; // listener type
import java.awt.event.KeyEvent; // (unused import from prior code, safe)

public class Game extends JFrame { // main window: holds menu and game screens
    private CardLayout cardLayout; // manager to swap screens
    private JPanel mainContainer; // container using CardLayout
    private GamePanel gamePanel; // gameplay panel
    private MainMenu menuPanel; // main menu panel

    public Game() { // set up window and screens
        super("Click K.O."); // window title
        setSize(500, 750); // window size
        setLocationRelativeTo(null); // center on screen
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE); // close app on X

        cardLayout = new CardLayout(); // create card layout
        mainContainer = new JPanel(cardLayout); // panel using cards
        add(mainContainer, BorderLayout.CENTER); // add to window

        gamePanel = new GamePanel(); // gameplay
        menuPanel = new MainMenu(this); // menu (needs Game to call back)

        mainContainer.add(menuPanel, "Menu"); // add menu card
        mainContainer.add(gamePanel, "Game"); // add game card

        setupMenuBar(); // build top menu bar

        setVisible(true); // show window
        SwingUtilities.invokeLater(() -> {
            menuPanel.requestFocusInWindow(); // focus menu after showing
            SoundManager.playMenuMusic(); // start menu music
        });
    }

    private void setupMenuBar() { // create menus and wire actions
        JMenuBar menuBar = new JMenuBar(); // bar across top

        JMenu gameMenu = new JMenu("Game"); // game actions
        JMenuItem newGameItem = new JMenuItem("New Game (Restart)"); // restart
        newGameItem.addActionListener(e -> handleNewGame()); // confirm + reset
        JMenuItem pauseResumeItem = new JMenuItem("Pause"); // pause toggle item
        pauseResumeItem.addActionListener(new ActionListener() { // update text on toggle
            @Override
            public void actionPerformed(ActionEvent e) {
                gamePanel.pausePause(); // toggle pause with countdown resume
                pauseResumeItem.setText(gamePanel.getPaused() ? "Resume" : "Pause"); // reflect state
            }
        });
        addWindowFocusListener(new java.awt.event.WindowAdapter() { // keep label in sync on refocus
            public void windowGainedFocus(java.awt.event.WindowEvent e) {
                if (gamePanel.isShowing()) {
                    pauseResumeItem.setText(gamePanel.getPaused() ? "Resume" : "Pause");
                }
            }
        });
        JMenuItem mainMenuItem = new JMenuItem("Main Menu"); // return to main menu
        mainMenuItem.addActionListener(e -> showMainMenu()); // switch to menu
        JMenuItem exitItem = new JMenuItem("Exit"); // exit app
        exitItem.addActionListener(e -> handleExit()); // confirm exit
        gameMenu.add(newGameItem);
        gameMenu.add(pauseResumeItem);
        gameMenu.addSeparator();
        gameMenu.add(mainMenuItem);
        gameMenu.addSeparator();
        gameMenu.add(exitItem);

        JMenu optionsMenu = new JMenu("Options"); // show options dialog
        JMenuItem optionsItem = new JMenuItem("Options...");
        optionsItem.addActionListener(e -> showOptionsDialog());
        optionsMenu.add(optionsItem);

        JMenu miscMenu = new JMenu("Misc."); // cosmetics grouping

        JMenu stageMenu = new JMenu("Stage"); // background choice
        ButtonGroup stageGroup = new ButtonGroup(); // radio group
        JRadioButtonMenuItem defaultStageItem = new JRadioButtonMenuItem("Default Ring", true); // default stage
        defaultStageItem.addActionListener(e -> handleCosmeticChange("stage", "assets/Ring Proper.png")); // switch image
        JRadioButtonMenuItem casinoStageItem = new JRadioButtonMenuItem("Casino"); // alt stage
        casinoStageItem.addActionListener(e -> handleCosmeticChange("stage", "assets/Ring Proper_blue.png"));
        JRadioButtonMenuItem retroStageItem = new JRadioButtonMenuItem("Retro"); // alt stage
        retroStageItem.addActionListener(e -> handleCosmeticChange("stage", "assets/Ring Proper_green.png"));
        stageGroup.add(defaultStageItem);
        stageGroup.add(casinoStageItem);
        stageGroup.add(retroStageItem);
        stageMenu.add(defaultStageItem);
        stageMenu.add(casinoStageItem);
        stageMenu.add(retroStageItem);
        JMenuItem colorPickerItem = new JMenuItem("Color Picker..."); // choose solid color instead of image
        colorPickerItem.addActionListener(e -> handleStageColorPicker());
        stageMenu.addSeparator();
        stageMenu.add(colorPickerItem);

        JMenu characterMenu = new JMenu("Character"); // player sprite set
        ButtonGroup characterGroup = new ButtonGroup(); // radio group
        JRadioButtonMenuItem defaultCharItem = new JRadioButtonMenuItem("Default Player", true); // default skin
        defaultCharItem.addActionListener(e -> handleCosmeticChange("character", "default")); // use assets/player/
        JRadioButtonMenuItem player2CharItem = new JRadioButtonMenuItem("Player 2"); // alt skin
        player2CharItem.addActionListener(e -> handleCosmeticChange("character", "player2")); // use assets/player_2/
        characterGroup.add(defaultCharItem);
        characterGroup.add(player2CharItem);
        characterMenu.add(defaultCharItem);
        characterMenu.add(player2CharItem);

        JMenu codeMenu = new JMenu("Code"); // secret code unlocks
        JMenuItem enterCodeItem = new JMenuItem("Enter Code..."); // input dialog
        enterCodeItem.addActionListener(e -> showCodeEntryDialog()); // pass to GamePanel
        codeMenu.add(enterCodeItem);

        miscMenu.add(stageMenu);
        miscMenu.add(characterMenu);
        miscMenu.add(codeMenu); // add code submenu

        JMenu helpMenu = new JMenu("Help"); // show control help
        JMenuItem controlsItem = new JMenuItem("Controls");
        controlsItem.addActionListener(e -> showControlsDialog());
        helpMenu.add(controlsItem);

        menuBar.add(gameMenu); // add menus to the bar
        menuBar.add(optionsMenu);
        menuBar.add(miscMenu);
        menuBar.add(Box.createHorizontalGlue()); // push Help to right side
        menuBar.add(helpMenu);

        setJMenuBar(menuBar); // install bar
    }

    private void showCodeEntryDialog() { // prompt for secret code and apply
        JTextField codeField = new JTextField(20); // text field
        JPanel panel = new JPanel(new BorderLayout(5, 5)); // small form
        panel.add(new JLabel("Enter Code:"), BorderLayout.WEST);
        panel.add(codeField, BorderLayout.CENTER);
        int result = JOptionPane.showConfirmDialog(
                this,
                panel,
                "Secret Code Entry",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE
        );
        if (result == JOptionPane.OK_OPTION) { // user confirmed
            String enteredCode = codeField.getText().trim(); // read input
            System.out.println("Code entered: " + enteredCode);
            gamePanel.applyDeveloperCode(enteredCode); // let GamePanel handle swaps
        }
    }

    private void handleCosmeticChange(String type, String value) { // route cosmetic changes
        if ("stage".equals(type)) {
            gamePanel.setStageBackground(value); // update background image
        } else if ("character".equals(type)) {
            gamePanel.getPlayer().setSpriteSet(value); // swap player skin
        }
    }

    private void handleStageColorPicker() { // choose a solid color background
        Color initialColor = Color.DARK_GRAY; // default pick
        Color newColor = JColorChooser.showDialog(this, "Choose Stage Color", initialColor); // open chooser
        if (newColor != null) {
            gamePanel.setStageColor(newColor); // apply color
        }
    }

    public void showControlsDialog() { // show basic controls help
        String message = "<html><h2>Game Controls</h2>"
                + "<p><b>A, D</b> &mdash; Left and Right Dodge</p>"
                + "<p><b>S</b> &mdash; Block</p>"
                + "<p><b>M1, M2</b> &mdash; Left and Right Punches (Mouse Clicks)</p>"
                + "<p><b>W</b> &mdash; Super Punch</p>"
                + "<p><b>P</b> &mdash; Pause / Resume Game</p>"
                + "</html>";
        JOptionPane.showMessageDialog(this, message, "Click K.O. Controls", JOptionPane.INFORMATION_MESSAGE);
    }

    private void showOptionsDialog() { // show options dialog with checkboxes
        JCheckBox sfxCheckBox = new JCheckBox("sfx", SoundManager.isSfxEnabled());
        JCheckBox musicCheckBox = new JCheckBox("music", SoundManager.isMusicEnabled());
        
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.add(sfxCheckBox);
        panel.add(musicCheckBox);
        
        int result = JOptionPane.showConfirmDialog(
                this,
                panel,
                "Options",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE
        );
        
        if (result == JOptionPane.OK_OPTION) { // user clicked OK
            SoundManager.setSfxEnabled(sfxCheckBox.isSelected());
            SoundManager.setMusicEnabled(musicCheckBox.isSelected());
            // restart music if enabled and we're on menu
            if (musicCheckBox.isSelected() && menuPanel.isShowing()) {
                SoundManager.playMenuMusic();
            } else if (musicCheckBox.isSelected() && gamePanel.isShowing()) {
                SoundManager.playGameMusic();
            }
        }
    }

    public void handleExit() { // confirm and exit application
        int result = JOptionPane.showConfirmDialog(
                this,
                "Are you sure you want to exit the game?",
                "Confirm Exit",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE
        );
        if (result == JOptionPane.YES_OPTION) { // confirmed
            System.exit(0); // quit
        }
    }

    private void handleNewGame() { // restart the game after confirmation
        int result = JOptionPane.showConfirmDialog(this,
                "Are you sure you want to start a new game? Current progress will be lost.",
                "New Game",
                JOptionPane.YES_NO_OPTION);
        if (result == JOptionPane.YES_OPTION) {
            showGamePanel(); // switch to game screen
            gamePanel.resetGameAfterKO(); // reset and start countdown
        }
    }

    public void showGamePanel() { // bring gameplay card to front and focus it
        SoundManager.stopMenuMusic(); // stop menu music
        SoundManager.playGameMusic(); // start game music
        cardLayout.show(mainContainer, "Game"); // show Game card
        SwingUtilities.invokeLater(() -> { // after layout
            gamePanel.requestFocusInWindow(); // ensure input focus
        });
    }

    public void showMainMenu() { // return to main menu
        SoundManager.stopGameMusic(); // stop game music
        SoundManager.playMenuMusic(); // start menu music
        cardLayout.show(mainContainer, "Menu"); // show Menu card
        SwingUtilities.invokeLater(() -> { // after layout
            menuPanel.requestFocusInWindow(); // ensure input focus
        });
    }

    public GamePanel getGamePanel() { // expose game panel for menu callbacks
        return gamePanel;
    }

    public static void main(String[] args) { // app entry point
        SwingUtilities.invokeLater(() -> new Game()); // build UI on EDT
    }
}