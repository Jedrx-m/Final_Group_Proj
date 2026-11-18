package Library; // package name

import java.awt.*; // graphics and fonts
import javax.swing.*; // images


public class Player { // the player character: sprites, state, input-driven actions
    private Font comboFont; // font for HUD/combo
    private long lastPunched; // last time a punch landed (for combo timer)
    private final long punchCountDisplay = 3000; // ms to keep combo text visible
    private float punchCountScale = 1.0f; // pop animation scale for combo text
    private Color punchCountColor = Color.white; // color of combo text
    private Opponent opp; // current opponent reference for applying damage

    private String spriteFolder = "assets/player/"; // where to load sprites from

    private Image pIDLE, pLEFTPUNCH, pRIGHTPUNCH, pLEFTDODGE, pRIGHTDODGE, pWINDUP, pSUPERPUNCH, pBLOCK, pHURT; // all sprites
    private final int baseX = -10, baseY = 250; // default draw position
    private int width = 500, height = 700; // draw size of player
    private int x = baseX, y = baseY; // current position (moves briefly on actions)

    public enum playerState {IDLE, PUNCHING_LEFT, PUNCHING_RIGHT, PUNCHING_SUPER, DODGING_LEFT, DODGING_RIGHT, WINDING_UP, BLOCKING, HURT, DEAD}; // all states
    private playerState currentState = playerState.IDLE; // current state
    private double health = 100; // current HP
    private double maxHealth = 100; // max HP
    private double superMeter = 0; // meter for super punch
    private double maxSuperMeter = 100; // meter max
    private boolean hasSuperPunchHit = false; // ensures super punch hits once
    private boolean superPunchUsedFullMeter = false; // whether super started at full meter

    private final double LOW_METER_STUN_CHANCE = 0.10; // stun chance when meter not full
    private final double FULL_METER_STUN_CHANCE = 0.75; // stun chance when meter was full

    private final int NORMAL_PUNCH_DAMAGE = 10; // damage for normal punch
    private final int SUPER_PUNCH_DAMAGE = 50; // damage for super punch
    private int punchCount = 0; // count punches for combo HUD

    private long lastActionTime; // last time an action started (for cooldowns)
    private long stateStartTime; // when current state began (for durations)
    private final int PUNCH_LUNGE = 75; // upward lunge on punch (pixels)
    private final int PUNCH_SIDE_OFFSET = 20; // side nudge on punch (pixels)
    private final int DODGE_DISTANCE = 30; // horizontal dodge distance
    private final int DODGE_DURATION = 600; // ms a dodge lasts
    private final int PUNCH_DURATION = 150; // ms a normal punch lasts
    private final int SUPER_WINDUP_TIME = 400; // ms before super launches
    private final int SUPER_PUNCH_DURATION = 600; // ms super punch lasts
    private final int BLOCK_OFFSET = 10; // slight backward move when blocking
    private final int HIT_STUN_SHAKE = 1000; // ms of hurt shake before idle
    private final int RETURN_SPEED_FAST = 10; // fast snap-back speed (pixels/frame)
    private final int RETURN_SPEED_SLOW = 4; // slow snap-ba    ck speed (pixels/frame)
    private final int punchCooldown = 350; // ms cooldown between punches
    private final int dodgeCooldown = 500; // ms cooldown between dodges
    private final int superCooldown = 1000; // ms cooldown between supers

    public Player() { // build player and load assets
        loadSprites(); // images
        loadFont(); // HUD font
    }

    public void setOpponent(Opponent opponent) { // link opponent to apply damage/stun
        this.opp = opponent;
    }

    public void setSpriteSet(String type) { // switch sprite theme (default/player2)
        System.out.println("Changing player sprite to: " + type);
        if ("player2".equals(type)) {
            this.spriteFolder = "assets/player_2/"; // alternate set
        } else {
            this.spriteFolder = "assets/player/"; // default set
        }
        loadSprites(); // reload with new folder
    }

    public void leftPunch(){ // quick left punch action
        if (!canAct(punchCooldown)) return;
        x -= PUNCH_SIDE_OFFSET; // move slightly left
        y -= PUNCH_LUNGE; // move up (lunge)
        currentState = playerState.PUNCHING_LEFT; // enter punch state
        stateStartTime = System.currentTimeMillis(); // mark start
        lastActionTime = stateStartTime; // set cooldown base
        buildSuperMeter(5); // gain meter
        punchCount++; // increase combo
        lastPunched = System.currentTimeMillis(); // for combo timer
        punchCountScale = 1.5f; // pop effect
        punchCountColor = Color.RED; // red pop
        if (opp != null) opp.takeDamage(NORMAL_PUNCH_DAMAGE); // apply damage
    }
    public void rightPunch(){ // quick right punch action
        if (!canAct(punchCooldown)) return;
        x += PUNCH_SIDE_OFFSET; // move slightly right
        y -= PUNCH_LUNGE; // move up (lunge)
        currentState = playerState.PUNCHING_RIGHT; // enter punch state
        stateStartTime = System.currentTimeMillis(); // mark start
        lastActionTime = System.currentTimeMillis(); // cooldown base
        buildSuperMeter(5); // gain meter
        punchCount++; // increase combo
        lastPunched = System.currentTimeMillis(); // combo timer
        punchCountScale = 1.5f; // pop effect
        punchCountColor = Color.RED; // red pop
        if (opp != null) opp.takeDamage(NORMAL_PUNCH_DAMAGE); // apply damage
    }
    public void superPunch(){ // start super (enters wind-up first)
        if (!canAct(superCooldown)) return;
        currentState = playerState.WINDING_UP; // wind-up phase
        stateStartTime = System.currentTimeMillis(); // mark start
        lastActionTime = stateStartTime; // cooldown base
        x -= 10; // small pose adjust
        y -= 10; // small pose adjust
    }
    public void leftDodge(){ // quick dodge left
        if (!canAct(dodgeCooldown)) return;
        x -= DODGE_DISTANCE; // move left
        currentState = playerState.DODGING_LEFT; // state
        stateStartTime = System.currentTimeMillis(); // mark start
        lastActionTime = stateStartTime; // cooldown base
    }
    public void rightDodge(){ // quick dodge right
        if (!canAct(dodgeCooldown)) return;
        x += DODGE_DISTANCE; // move right
        currentState = playerState.DODGING_RIGHT; // state
        stateStartTime = System.currentTimeMillis(); // mark start
        lastActionTime = stateStartTime; // cooldown base
    }
    public void block(){ // hold block (reduces damage taken)
        if (currentState != playerState.IDLE) return;
        x -= BLOCK_OFFSET; // tiny move back
        currentState = playerState.BLOCKING; // state
        stateStartTime = System.currentTimeMillis(); // mark start
        lastActionTime = stateStartTime; // set action time
    }
    public void recoil(){} // reserved hook for future use


    public void takeDamage(double amount) { // apply incoming damage and change state
        if (currentState == playerState.HURT || currentState == playerState.DEAD) return;
        double appliedDamage = amount; // start with full damage
        if (currentState == playerState.BLOCKING) { // blocking reduces damage
            appliedDamage *= 0.25; // 25% damage
            health -= appliedDamage; // subtract
            health = Math.max(0, health); // clamp
            System.out.println("Player blocked the hit! Damage reduced to: " + appliedDamage + " | Health: " + health);
            return; // exit early
        }
        health -= appliedDamage; // full damage
        health = Math.max(0, health); // clamp
        System.out.println("Player took damage: " + appliedDamage + " | Health: " + health);
        if (health <= 0) { // dead
            currentState = playerState.DEAD;
        } else { // hurt state
            currentState = playerState.HURT;
            x -= 10; // tiny knockback
            stateStartTime = System.currentTimeMillis(); // start hurt timer
        }
    }

    public void resetOnDeath() { // restore default stats/position/state
        health = maxHealth;
        superMeter = 0;
        punchCount = 0;
        punchCountScale = 1.0f;
        punchCountColor = Color.WHITE;

        x = baseX;
        y = baseY;
        currentState = playerState.IDLE;

        lastActionTime = 0;
        stateStartTime = 0;
        lastPunched = 0;
        hasSuperPunchHit = false;
        superPunchUsedFullMeter = false; // clear full-meter flag

        System.out.println("Player has respawned.");
    }

    public boolean canAct(long cooldown){ // true if cooldown passed and player is idle
        return System.currentTimeMillis() - lastActionTime >= cooldown && currentState == playerState.IDLE;
    }

    public void buildSuperMeter(int meterGained){ // add to super meter, capped
        superMeter = Math.min(superMeter + meterGained, maxSuperMeter);
    }

    private void loadSprites() { // read and scale all player images
        try {
            pIDLE = new ImageIcon(spriteFolder + "play_idle.png").getImage().getScaledInstance(width, height, Image.SCALE_SMOOTH);
            pLEFTPUNCH = new ImageIcon(spriteFolder + "play_punchL.png").getImage().getScaledInstance(width, height, Image.SCALE_SMOOTH);
            pRIGHTPUNCH = new ImageIcon(spriteFolder + "play_punchR.png").getImage().getScaledInstance(width, height, Image.SCALE_SMOOTH);
            pLEFTDODGE = new ImageIcon(spriteFolder + "play_dodgeL.png").getImage().getScaledInstance(width, height, Image.SCALE_SMOOTH);
            pRIGHTDODGE = new ImageIcon(spriteFolder + "play_dodgeR.png").getImage().getScaledInstance(width, height, Image.SCALE_SMOOTH);
            pWINDUP = new ImageIcon(spriteFolder + "play_windup.png").getImage().getScaledInstance(width, height, Image.SCALE_SMOOTH);
            pSUPERPUNCH = new ImageIcon(spriteFolder + "play_super.png").getImage().getScaledInstance(width, height, Image.SCALE_SMOOTH);
            pBLOCK = new ImageIcon(spriteFolder + "play_block.png").getImage().getScaledInstance(width, height, Image.SCALE_SMOOTH);
            pHURT = new ImageIcon(spriteFolder + "play_hurt.png").getImage().getScaledInstance(width, height, Image.SCALE_SMOOTH);
        } catch (Exception e) {
            System.out.println("Failed to load player sprites from " + spriteFolder + ": " + e.getMessage());
            if (!spriteFolder.equals("assets/player/")) {
                this.spriteFolder = "assets/player/";
                loadSprites();
            }
        }
    }

    public void drawComboCounter(Graphics g) { // draw xN text briefly after punches
        if (punchCount <= 0) return; // nothing to draw

        long now = System.currentTimeMillis();
        if (now - lastPunched > punchCountDisplay) return; // expired

        float fontSize = 24f * punchCountScale; // pop scale
        g.setFont(comboFont.deriveFont(Font.BOLD, fontSize));
        g.setColor(punchCountColor);

        String comboText = "x" + punchCount; // e.g., x3
        FontMetrics fm = g.getFontMetrics();
        int textWidth = fm.stringWidth(comboText);
        int xPos = 430 - textWidth / 2;
        int yPos = 100;

        g.setColor(Color.BLACK);
        g.drawString(comboText, xPos + 2, yPos + 2);
        g.setColor(punchCountColor);
        g.drawString(comboText, xPos, yPos);
    }

    private void loadFont() { // load custom font (fallback to Arial)
        try {
            comboFont = Font.createFont(Font.TRUETYPE_FONT, new java.io.File("assets/yoster.ttf")).deriveFont(Font.BOLD, 24f);
            GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
            ge.registerFont(comboFont);
        } catch (Exception e) {
            System.out.println("Failed to load combo font: " + e.getMessage());
            comboFont = new Font("ARIAL", Font.BOLD, 24);
        }
    }

    public void draw(Graphics g) { // draw HUD bars, combo text, and sprite
        Image currentSprite = pIDLE; // choose sprite by state
        switch (currentState) {
            case PUNCHING_LEFT: currentSprite = pLEFTPUNCH; break;
            case PUNCHING_RIGHT: currentSprite = pRIGHTPUNCH; break;
            case DODGING_LEFT: currentSprite = pLEFTDODGE; break;
            case DODGING_RIGHT: currentSprite = pRIGHTDODGE; break;
            case WINDING_UP: currentSprite = pWINDUP; break;
            case PUNCHING_SUPER: currentSprite = pSUPERPUNCH; break;
            case BLOCKING: currentSprite = pBLOCK; break;
            case HURT: currentSprite = pHURT; break;
            case IDLE:
            default: currentSprite = pIDLE; break;
        }

        g.setColor(Color.GREEN); // health bar fill
        int barWidth = (int) ((health / maxHealth) * 200);
        g.fillRect(20, 20, barWidth, 20);
        g.setColor(Color.WHITE); // health bar border
        g.drawRect(20, 20, 200, 20);

        int superBarWidth = (int) ((superMeter / maxSuperMeter) * 200); // super meter width
        long currentTime = System.currentTimeMillis(); // for flashing effect
        boolean flashOn = (currentTime / 200) % 2 == 0; // blink toggle
        if (superMeter >= maxSuperMeter) { // full meter: orange flashing
            g.setColor(flashOn ? new Color(255, 165, 0) : new Color(255, 200, 50));
        } else { // partial: blue based on fill
            int brightness = (int) (100 + (superMeter / maxSuperMeter) * 155);
            g.setColor(new Color(brightness, brightness, 255));
        }
        g.fillRect(20, 50, superBarWidth, 15);
        g.setColor(Color.WHITE);
        g.drawRect(20, 50, 200, 15);
        g.setFont(comboFont.deriveFont(24f));
        g.drawString("SUPER", 20, 85);

        drawComboCounter(g); // draw xN combo

        int drawX = x; // sprite position (shakes when hurt)
        int drawY = y;
        if (currentState == playerState.HURT) { // small shake
            drawX += (int)(Math.random() * 6 - 3);
            drawY += (int)(Math.random() * 4 - 2);
        }
        g.drawImage(currentSprite, drawX, drawY, width, height, null); // draw sprite

    }

    public void update() { // progress durations, smooth return, handle super hit window
        long now = System.currentTimeMillis();
        if (currentState != playerState.DODGING_LEFT && currentState != playerState.DODGING_RIGHT) { // snap back to base when not dodging
            if (Math.abs(x - baseX) > 3) {
                int speed = (currentState == playerState.PUNCHING_LEFT || currentState == playerState.PUNCHING_RIGHT)
                        ? RETURN_SPEED_FAST : RETURN_SPEED_SLOW;
                x += (x < baseX) ? speed : -speed;
            } else x = baseX;

            if (Math.abs(y - baseY) > 3) {
                int speed = RETURN_SPEED_SLOW;
                y += (y < baseY) ? speed : -speed;
            } else y = baseY;
        }

        if (Math.abs(y - baseY) > 3) {
            int speed = (currentState == playerState.DODGING_LEFT || currentState == playerState.DODGING_RIGHT)
                    ? RETURN_SPEED_SLOW : RETURN_SPEED_FAST;
            y += (y < baseY) ? speed : -speed;
        } else y = baseY;

        if (currentState == playerState.PUNCHING_LEFT || currentState == playerState.PUNCHING_RIGHT) { // end normal punch after duration
            if (now - stateStartTime > PUNCH_DURATION) {
                currentState = playerState.IDLE;
            }
        }

        if (currentState == playerState.DODGING_LEFT || currentState == playerState.DODGING_RIGHT) { // end dodge after duration
            if (now - stateStartTime > DODGE_DURATION) {
                currentState = playerState.IDLE;
            }
        }

        if (currentState == playerState.WINDING_UP) { // after wind-up, launch super
            if (now - stateStartTime > SUPER_WINDUP_TIME) {
                superPunchUsedFullMeter = (superMeter >= maxSuperMeter); // remember if full meter
                currentState = playerState.PUNCHING_SUPER; // start super punch
                stateStartTime = now; // mark start
                x += 50; // forward lunge
                y -= 20; // upward lunge
                punchCount++; // counts as a combo hit
                if (superMeter < maxSuperMeter) { // pay small cost if not full
                    superMeter -=10;
                }
                else if (superMeter >= maxSuperMeter) { // spend entire bar if full
                    superMeter -= maxSuperMeter;
                }
                hasSuperPunchHit = false; // allow one-time hit
            }
        }

        if (currentState == playerState.PUNCHING_SUPER) { // during super, apply one hit and maybe stun
            if (!hasSuperPunchHit && opp != null) { // hit once
                opp.takeDamage(SUPER_PUNCH_DAMAGE);
                double currentStunChance = superPunchUsedFullMeter ? FULL_METER_STUN_CHANCE : LOW_METER_STUN_CHANCE; // pick chance
                if (Math.random() < currentStunChance) { // maybe stun for 3s
                    opp.stun(3000);
                    System.out.println("SUPER PUNCH STUNNED THE OPPONENT! (Chance: " + currentStunChance + ")");
                }
                hasSuperPunchHit = true;
            }

            if (now - stateStartTime > SUPER_PUNCH_DURATION) { // end super
                currentState = playerState.IDLE; // back to idle
                superPunchUsedFullMeter = false; // clear flag
            }
        }

        if (currentState == playerState.HURT) { // after hurt time, go idle
            if (now - stateStartTime > HIT_STUN_SHAKE) {
                currentState = playerState.IDLE;
            }
        }

        if (now - lastPunched > punchCountDisplay) { // combo text timeout
            punchCount = 0;
            punchCountScale = 1.0f;
        }

        if (punchCountScale > 1.0f) { // animate combo pop shrinking back to 1.0
            punchCountScale -= 0.05f;
            if (punchCountScale <= 1.0f) {
                punchCountScale = 1.0f;
                punchCountColor = Color.WHITE;
            }
        }
    }

    public playerState getState() {return currentState;} // expose current state
    public void setState(playerState newState) { // force a new state and reset its timer
        currentState = newState;
        stateStartTime = System.currentTimeMillis();
    }
    public double getSuperMeter() {return superMeter;} // read current meter
    public double getMaxSuperMeter(){return maxSuperMeter;} // read meter max
    public double getHealth(){return health;} // read current health
}
