package Main; // package name

import javax.swing.*; // Swing components and Timer
import java.awt.*; // Graphics
import java.awt.event.*; // input events
import Library.Opponent; // opponent class
import Library.Player; // player class

public class GamePanel extends JPanel implements KeyListener, MouseListener { // main gameplay surface

    public enum GameState { MENU, COUNTDOWN, PLAYING, PAUSED, GAME_OVER } // high-level states

    private GameState currentState = GameState.MENU; // used mainly for GAME_OVER overlay
    private Font comboFont; // UI font
    private Image backgroundImage; // stage background image
    private Color backgroundColor = null; // solid color background (if chosen)

    private Opponent opponent1, opponent2, opponent3; // normal opponents
    private Opponent opponent4, opponent5, opponent6; // developer opponents
    private Opponent[] activeOpponents; // which set is currently used
    private Opponent currentOpponent; // opponent on screen now
    private Player player; // the player
    private boolean hasAppliedSuperDamage = false; // ensures super damage applied once per super
    private boolean hasOpponentAppliedDamage = false; // ensures each enemy attack hits once
    public boolean isPaused = true; // start paused (menu flow will unpause)

    private boolean isCountingDown = false; // countdown active flag
    private int countdownNumber = 0; // countdown value
    private Timer countdownTimer; // timer for countdown 3..GO
    private boolean isKO = false; // KO overlay flag
    private Timer koTimer; // timer before reset after KO
    private Timer winTimer; // timer before reset after YOU WIN

    private int currentRound = 1; // 1-based index in activeOpponents
    private boolean isTransitioning = false; // between opponents fade flag
    private float opponentFadeAlpha = 1.0f; // fade alpha

    public GamePanel() { // construct game scene
        loadBackground("assets/Ring Proper.png"); // default stage
        setFocusable(true); // receive key events
        addKeyListener(this); // key input
        addMouseListener(this); // mouse input

        loadFont(); // load UI font

        player = new Player(); // create player

        opponent1 = new Opponent("opponent_1", 60, 60, 200, 20, 2, player); // easy
        opponent2 = new Opponent("opponent_2", 60, 60, 300, 20, 1, player); // medium
        opponent3 = new Opponent("opponent_3", 60, 60, 400, 30, 1, player); // hard

        opponent4 = new Opponent("dev_loper1", 60, 60, 500, 20, 1, player); // dev set 1
        opponent5 = new Opponent("dev_loper2", 60, 60, 500, 30, 1, player); // dev set 2
        opponent6 = new Opponent("dev_loper3", 60, 60, 500, 50, 1, player); // dev set 3

        activeOpponents = new Opponent[]{opponent1, opponent2, opponent3}; // default roster
        currentOpponent = activeOpponents[0]; // start with first

        Timer gameLoop = new Timer(16, e -> { // ~60 FPS loop (event-driven timer)
            update(); // advance simulation
            repaint(); // redraw
        });
        gameLoop.start(); // start loop
    }

    public void applyDeveloperCode(String code) { // unlock dev opponents via code or return to normal
        if ("developer".equals(code)) {
            if (activeOpponents[0] != opponent4) { // check if not already in developer mode
                activeOpponents = new Opponent[]{opponent4, opponent5, opponent6}; // swap roster
                SoundManager.setDeveloperMode(true); // enable developer mode
                if (currentState == GameState.PLAYING || currentState == GameState.PAUSED) { // if mid-game, reset round
                    resetGameAfterKO();
                    SoundManager.playDeveloperMusic(); // switch to developer music
                    JOptionPane.showMessageDialog(this, "Developer Mode Unlocked! New opponents await.", "Code Accepted", JOptionPane.INFORMATION_MESSAGE);
                } else { // if not playing yet
                    currentOpponent = activeOpponents[0];
                    currentRound = 1;
                    JOptionPane.showMessageDialog(this, "Developer Opponents Unlocked! Press PLAY to begin.", "Code Accepted", JOptionPane.INFORMATION_MESSAGE);
                }
            } else {
                JOptionPane.showMessageDialog(this, "Developer Mode is already active.", "Code Status", JOptionPane.INFORMATION_MESSAGE);
            }
        } else if ("normal".equals(code)) { // return to normal mode
            if (activeOpponents[0] == opponent4) { // check if currently in developer mode
                activeOpponents = new Opponent[]{opponent1, opponent2, opponent3}; // restore normal roster
                SoundManager.setDeveloperMode(false); // disable developer mode
                if (currentState == GameState.PLAYING || currentState == GameState.PAUSED) { // if mid-game, reset round
                    resetGameAfterKO();
                    SoundManager.playGameMusic(); // switch back to normal game music
                    JOptionPane.showMessageDialog(this, "Normal Mode Restored! Normal opponents restored.", "Code Accepted", JOptionPane.INFORMATION_MESSAGE);
                } else { // if not playing yet
                    currentOpponent = activeOpponents[0];
                    currentRound = 1;
                    JOptionPane.showMessageDialog(this, "Normal Mode Restored! Press PLAY to begin.", "Code Accepted", JOptionPane.INFORMATION_MESSAGE);
                }
            } else {
                JOptionPane.showMessageDialog(this, "Already in Normal Mode.", "Code Status", JOptionPane.INFORMATION_MESSAGE);
            }
        } else { // invalid code
            JOptionPane.showMessageDialog(this, "Code Rejected: Invalid or unknown code.", "Code Rejected", JOptionPane.WARNING_MESSAGE);
        }
    }


    private void loadBackground(String path) { // set image background (clears solid color)
        this.backgroundColor = null;
        try {
            this.backgroundImage = new ImageIcon(path).getImage();
        } catch (Exception e) {
            System.err.println("Failed to load background image: " + path + ". Error: " + e.getMessage());
            this.backgroundImage = null;
        }
    }

    public void setStageBackground(String path) { // use an image background
        loadBackground(path);
        repaint();
    }

    public void setStageColor(Color color) { // use a solid color background
        this.backgroundImage = null;
        this.backgroundColor = color;
        repaint();
    }


    @Override
    public void paintComponent(Graphics g) { // draw background, opponent, player, and overlays
        super.paintComponent(g);

        if (backgroundColor != null) { // solid color background
            g.setColor(backgroundColor);
            g.fillRect(0, 0, getWidth(), getHeight());
        } else if (backgroundImage != null) { // image background
            g.drawImage(backgroundImage, 0, 0, getWidth(), getHeight(), this);
        } else { // fallback
            g.setColor(Color.BLACK);
            g.fillRect(0, 0, getWidth(), getHeight());
        }

        if (isTransitioning && currentOpponent.getHealth() <= 0) { // fade out defeated opponent
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opponentFadeAlpha));
            currentOpponent.draw(g2);
            g2.dispose();
        } else {
            currentOpponent.draw(g); // normal draw
        }

        player.draw(g); // draw player and HUD

        if (isPaused && !isCountingDown && !isKO) { // pause overlay
            g.setColor(new Color(0, 0, 0, 150));
            g.fillRect(0, 0, getWidth(), getHeight());
            g.setColor(Color.WHITE);
            g.setFont(comboFont.deriveFont(Font.BOLD, 48f));
            String pauseText = "PAUSED";
            FontMetrics fm = g.getFontMetrics();
            int textWidth = fm.stringWidth(pauseText);
            g.drawString(pauseText, (getWidth() - textWidth) / 2, getHeight() / 2);
        }

        if (isCountingDown) { // countdown overlay
            g.setColor(new Color(0, 0, 0, 150));
            g.fillRect(0, 0, getWidth(), getHeight());
            g.setColor(Color.WHITE);
            g.setFont(comboFont.deriveFont(Font.BOLD, 72f));
            String countText = (countdownNumber > 0) ? String.valueOf(countdownNumber) : "GO!";
            FontMetrics fm = g.getFontMetrics();
            int textWidth = fm.stringWidth(countText);
            g.drawString(countText, (getWidth() - textWidth) / 2, getHeight() / 2);
        }

        if (currentState == GameState.GAME_OVER) { // win overlay
            g.setColor(new Color(0, 0, 0, 200));
            g.fillRect(0, 0, getWidth(), getHeight());
            g.setColor(Color.YELLOW);
            g.setFont(comboFont.deriveFont(Font.BOLD, 64f));
            String text = "YOU WIN!";
            FontMetrics fm = g.getFontMetrics();
            g.drawString(text, (getWidth() - fm.stringWidth(text)) / 2, getHeight() / 2);
        }

        if (isKO) { // KO overlay
            g.setColor(new Color(0, 0, 0, 180));
            g.fillRect(0, 0, getWidth(), getHeight());
            g.setColor(Color.RED);
            g.setFont(comboFont.deriveFont(Font.BOLD, 96f));
            String koText = "K.O.";
            FontMetrics fm = g.getFontMetrics();
            int textWidth = fm.stringWidth(koText);
            g.drawString(koText, (getWidth() - textWidth) / 2, getHeight() / 2);
        }
    }

    private void update() { // per-frame updates (skips when paused/countdown/game over)
        if (isPaused || isCountingDown || currentState == GameState.GAME_OVER) return;
        player.update(); // update player state machine
        currentOpponent.update(); // update opponent AI/state
        superPunchChecker(); // apply player's super damage if applicable
        handleOpponentAttack(); // apply opponent damage when attacks connect
        checkOpponentDefeat(); // fade and switch to next opponent
        if (player.getHealth() <= 0 && !isKO) { // player KO check
            handlePlayerKO();
        }
    }

    private void checkOpponentDefeat() { // start fade-out when opponent reaches 0 HP
        if (isTransitioning) return; // already transitioning
        if (currentOpponent.getHealth() <= 0) {
            isTransitioning = true; // begin fade
            currentOpponent.setCurrentState(Opponent.opponentState.STUNNED); // show stunned sprite while fading
            Timer fadeTimer = new Timer(100, e -> { // reduce alpha gradually
                opponentFadeAlpha -= 0.05f;
                if (opponentFadeAlpha <= 0f) { // when fully transparent
                    ((Timer) e.getSource()).stop();
                    spawnNextOpponent(); // bring next one in
                }
            });
            fadeTimer.start();
        }
    }

    private void spawnNextOpponent() { // load next opponent or end game
        opponentFadeAlpha = 1.0f; // reset fade
        if (currentRound < activeOpponents.length) { // have more opponents
            currentOpponent = activeOpponents[currentRound]; // next
            currentRound++; // advance round counter
            currentOpponent.setCurrentState(Opponent.opponentState.TAUNTING); // taunt first
            Timer tauntTimer = new Timer(3000, e -> { // after 3s go idle
                currentOpponent.setCurrentState(Opponent.opponentState.IDLE);
                isTransitioning = false;
                ((Timer) e.getSource()).stop();
            });
            tauntTimer.setRepeats(false);
            tauntTimer.start();
        } else { // no more opponents
            System.out.println("All opponents defeated!");
            currentState = GameState.GAME_OVER; // show win overlay
            winTimer = new Timer(5000, e -> {
                ((Timer) e.getSource()).stop();
                resetGameAfterKO();
            });
            winTimer.setRepeats(false);
            winTimer.start();
            return;
        }
    }

    private void handleOpponentAttack() { // compute hits from opponent to player
        Opponent.opponentState oppState = currentOpponent.getCurrentState(); // current enemy state
        if (hasOpponentAppliedDamage) { // wait until attack state changes to re-allow hits
            if (oppState != Opponent.opponentState.ATTACKING_LEFT &&
                    oppState != Opponent.opponentState.ATTACKING_RIGHT &&
                    oppState != Opponent.opponentState.SUPERPUNCHING) {
                hasOpponentAppliedDamage = false; // next attack can hit again
            }
            return;
        }
        if (oppState == Opponent.opponentState.ATTACKING_LEFT ||
                oppState == Opponent.opponentState.ATTACKING_RIGHT) { // normal attack
            boolean isAttackLeft = (oppState == Opponent.opponentState.ATTACKING_LEFT);
            boolean isDodgingRight = (player.getState() == Player.playerState.DODGING_RIGHT);
            boolean isDodgingLeft = (player.getState() == Player.playerState.DODGING_LEFT);
            if ((isAttackLeft && isDodgingLeft) || (!isAttackLeft && isDodgingRight)) { // correct slip
                System.out.println("Player successfully slipped the attack!");
                hasOpponentAppliedDamage = true; // consume attack
                return;
            }
            double baseDamage = currentOpponent.getDamage();
            double finalDamage = baseDamage; // ready for future modifiers
            player.takeDamage(finalDamage); // apply
            hasOpponentAppliedDamage = true; // prevent multiple hits
        }
        if (oppState == Opponent.opponentState.SUPERPUNCHING) { // super punch deals more
            double baseDamage = currentOpponent.getDamage();
            double finalDamage = baseDamage * 2; // double damage
            player.takeDamage(finalDamage);
            hasOpponentAppliedDamage = true;
        }
    }

    private void superPunchChecker() { // compute player's super damage window
        if (player.getState() == Player.playerState.PUNCHING_SUPER && !hasAppliedSuperDamage) { // only once per super
            Opponent.opponentState oppState = currentOpponent.getCurrentState();
            boolean isVulnerable = (oppState == Opponent.opponentState.IDLE || // safe to hit
                    oppState == Opponent.opponentState.WINDINGL ||
                    oppState == Opponent.opponentState.WINDINGR ||
                    oppState == Opponent.opponentState.TAUNTING);
            if (isVulnerable) {
                boolean fullMeter = player.getSuperMeter() >= player.getMaxSuperMeter(); // check full
                double damage = fullMeter ? 100.0 : 15.0; // bonus if full
                currentOpponent.takeDamage(damage); // apply damage
            }
            hasAppliedSuperDamage = true; // prevent repeat
        }
        if (player.getState() == Player.playerState.PUNCHING_SUPER) { // while super, let enemy hit again next time
            hasOpponentAppliedDamage = false;
        }
    }

    @Override
    public void keyTyped(KeyEvent e) {} // unused

    @Override
    public void keyPressed(KeyEvent e) { // react to keyboard
        int key = e.getKeyCode();
        if (key == KeyEvent.VK_P) { // pause/resume with countdown
            pausePause();
            return;
        }
        if (isPaused || isCountingDown || currentState == GameState.GAME_OVER) return; // ignore during overlays
        if (key == KeyEvent.VK_A) player.leftDodge(); // left dodge
        if (key == KeyEvent.VK_D) player.rightDodge(); // right dodge
        if (key == KeyEvent.VK_W) { // super punch
            player.superPunch();
            hasAppliedSuperDamage = false; // allow damage on this super
        }
        if (key == KeyEvent.VK_S) player.block(); // block
    }

    @Override
    public void keyReleased(KeyEvent e) { // release block when S is released
        if (e.getKeyCode() == KeyEvent.VK_S) {
            player.setState(Player.playerState.IDLE);
        }
    }

    @Override public void mouseClicked(MouseEvent e) { // mouse punches
        if (isPaused || isCountingDown || currentState == GameState.GAME_OVER) return; // ignore during overlays
        if (SwingUtilities.isLeftMouseButton(e)) { // left click = left punch
            Player.playerState oldState = player.getState(); // remember old state
            player.leftPunch();
            if (player.getState() == Player.playerState.PUNCHING_LEFT) { // if punch state took (cooldown allowed it)
                currentOpponent.takeDamage(10);
                // only play sound if state changed from non-punching to punching (punch actually executed)
                if (oldState != Player.playerState.PUNCHING_LEFT && oldState != Player.playerState.PUNCHING_RIGHT) {
                    SoundManager.playClickSound(); // play click sound
                }
            }
        }
        if (SwingUtilities.isRightMouseButton(e)) { // right click = right punch
            Player.playerState oldState = player.getState(); // remember old state
            player.rightPunch();
            if (player.getState() == Player.playerState.PUNCHING_RIGHT) { // if punch state took (cooldown allowed it)
                currentOpponent.takeDamage(10);
                // only play sound if state changed from non-punching to punching (punch actually executed)
                if (oldState != Player.playerState.PUNCHING_LEFT && oldState != Player.playerState.PUNCHING_RIGHT) {
                    SoundManager.playClickSound(); // play click sound
                }
            }
        }
    }

    @Override public void mousePressed(MouseEvent e) {} // unused
    @Override public void mouseReleased(MouseEvent e) {} // unused
    @Override public void mouseEntered(MouseEvent e) {} // unused
    @Override public void mouseExited(MouseEvent e) {} // unused

    public void pausePause() { // toggle pause; on resume, run countdown
        if (isCountingDown) return; // avoid stacking
        if (!isPaused) { // going to pause now
            isPaused = true;
            currentOpponent.pause(); // pause enemy timers
            System.out.println("Game Paused");
        } else if (isPaused) { // going to resume via countdown
            startCountdown();
        }
    }

    private void startCountdown() { // 3..2..1..GO resume sequence
        isCountingDown = true;
        countdownNumber = 4; // will render 3,2,1,GO
        countdownTimer = new Timer(1000, e -> { // tick each second
            countdownNumber--; // next number
            repaint(); // update overlay
            if (countdownNumber < 0) { // after GO
                ((Timer) e.getSource()).stop();
                isCountingDown = false;
                isPaused = false; // resume game
                currentOpponent.resume(); // resume enemy timers
                repaint();
            }
        });
        countdownTimer.setInitialDelay(0); // show immediately
        countdownTimer.start(); // begin
    }

    private void loadFont() { // load stage/UI font
        try {
            comboFont = Font.createFont(Font.TRUETYPE_FONT, new java.io.File("assets/yoster.ttf"))
                    .deriveFont(Font.BOLD, 24f);
            GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
            ge.registerFont(comboFont);
        } catch (Exception e) {
            System.out.println("Failed to load combo font: " + e.getMessage());
            comboFont = new Font("Arial", Font.BOLD, 24);
        }
    }

    private void handlePlayerKO() { // show KO, then reset after delay
        isKO = true; // KO overlay on
        isPaused = true; // stop updates
        System.out.println("K.O.! Player defeated.");
        koTimer = new Timer(3000, e -> { // wait 3 seconds
            ((Timer) e.getSource()).stop();
            resetGameAfterKO(); // reset match
        });
        koTimer.setRepeats(false);
        koTimer.start();
    }

    public void resetGameAfterKO() { // restore default round state and start countdown
        if (winTimer != null && winTimer.isRunning()) {
            winTimer.stop();
        }
        System.out.println("Resetting game after K.O.");
        player.resetOnDeath(); // reset player stats/state
        opponent1.resetOnPlayerDeath();
        opponent2.resetOnPlayerDeath();
        opponent3.resetOnPlayerDeath();
        opponent4.resetOnPlayerDeath();
        opponent5.resetOnPlayerDeath();
        opponent6.resetOnPlayerDeath();
        loadBackground("assets/Ring Proper.png"); // default stage
        currentOpponent = activeOpponents[0]; // first opponent
        currentRound = 1; // round 1
        isPaused = false; // unpause
        isKO = false; // clear KO
        currentState = GameState.MENU; // Reset game state from GAME_OVER or PLAYING
        startCountdown(); // resume via countdown
    }

    public boolean getPaused() { return isPaused; } // expose pause flag
    public void setPaused(boolean paused) { this.isPaused = paused; } // set pause flag
    public Player getPlayer() { return player; } // expose player
}