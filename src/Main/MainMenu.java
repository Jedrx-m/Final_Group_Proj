package Main; // package name

import javax.swing.*; // Swing UI
import java.awt.*; // layout, graphics

public class MainMenu extends JPanel { // title screen with background, logo, and buttons
    private Font customFont; // menu font
    private JLabel logoLabel; // shows the logo
    private ImageIcon logoIcon; // logo image
    private Game game; // reference to switch screens and show dialogs

    public MainMenu(Game game) { // build the main menu
        this.game = game; // store reference

        ImageIcon bgIcon = new ImageIcon("assets/MenuBackground.png"); // background image
        JLabel background = new JLabel(bgIcon); // label with image as content
        background.setLayout(new BorderLayout()); // allow adding children
        setLayout(new BorderLayout()); // base layout for this panel
        add(background, BorderLayout.CENTER); // put background inside

        try { // try to load custom pixel font
            customFont = Font.createFont(Font.TRUETYPE_FONT, new java.io.File("assets/yoster.ttf")).deriveFont(32f);
            GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
            ge.registerFont(customFont);
        } catch (Exception e) { // fallback if font not found
            customFont = new Font("Arial", Font.BOLD, 32);
        }

        logoIcon = new ImageIcon("assets/Click.gif"); // animated logo
        Image img = logoIcon.getImage().getScaledInstance(350, 105, Image.SCALE_DEFAULT); // scale neatly
        logoIcon = new ImageIcon(img);

        logoLabel = new JLabel(logoIcon); // place logo in a label
        logoLabel.setHorizontalAlignment(JLabel.CENTER); // center it

        JPanel titlePanel = new JPanel(new BorderLayout()); // transparent container for logo
        titlePanel.setOpaque(false);
        titlePanel.add(logoLabel, BorderLayout.CENTER);
        titlePanel.setBorder(BorderFactory.createEmptyBorder(60, 40, 100, 40)); // spacing

        JButton playButton = new RoundedButton("PLAY"); // start game
        JButton optionsButton = new RoundedButton("CONTROLS"); // show controls
        JButton exitButton = new RoundedButton("EXIT"); // exit app

        playButton.setFont(customFont); // apply font
        optionsButton.setFont(customFont);
        exitButton.setFont(customFont);

        playButton.addActionListener(e -> { // PLAY: blink, open game, resume via countdown
            blinkButton(playButton);
            SwingUtilities.invokeLater(() -> game.showGamePanel()); // switch card
            game.getGamePanel().pausePause(); // unpause through countdown
        });

        optionsButton.addActionListener(e -> { // CONTROLS: blink, show dialog
            blinkButton(optionsButton);
            game.showControlsDialog();
        });

        exitButton.addActionListener(e -> { // EXIT: blink, confirm exit
            blinkButton(exitButton);
            SwingUtilities.invokeLater(() -> game.handleExit());
        });

        JPanel buttonPanel = new JPanel(new GridLayout(3, 1, 20, 20)); // vertical stack of buttons
        buttonPanel.setOpaque(false);
        buttonPanel.add(playButton);
        buttonPanel.add(optionsButton);
        buttonPanel.add(exitButton);
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(0, 80, 100, 80)); // spacing to edges

        JPanel mainPanel = new JPanel(new BorderLayout()); // combine logo and buttons
        mainPanel.setOpaque(false);
        mainPanel.add(titlePanel, BorderLayout.NORTH);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        background.add(mainPanel, BorderLayout.CENTER); // place over the background image
    }

    private void blinkButton(JButton button) { // quick blink feedback on click
        new Thread(() -> {
            try {
                for (int i = 0; i < 2; i++) {
                    button.setForeground(Color.BLACK);
                    Thread.sleep(100);
                    button.setForeground(Color.WHITE);
                    Thread.sleep(100);
                }
            } catch (InterruptedException ignored) {}
        }).start();
    }

    class RoundedButton extends JButton { // custom rounded brown button
        public RoundedButton(String text) {
            super(text);
            setContentAreaFilled(false); // we will paint background ourselves
            setFocusPainted(false); // remove focus ring
            setBorderPainted(false); // no border outline
            setForeground(Color.WHITE); // white text
        }

        @Override
        protected void paintComponent(Graphics g) { // draw rounded background with hover/press colors
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            if (getModel().isPressed()) { // darker on press
                g2.setColor(new Color(205, 133, 63, 220));
            } else if (getModel().isRollover()) { // medium on hover
                g2.setColor(new Color(160, 82, 45, 200));
            } else { // normal
                g2.setColor(new Color(139, 69, 19, 180));
            }

            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 30, 30); // rounded rect
            super.paintComponent(g); // draw text
            g2.dispose();
        }
    }
}
