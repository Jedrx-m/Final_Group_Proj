package Library; // package name
import javax.swing.*; // for Swing Timer
import java.awt.*; // for Graphics and Image
import java.awt.event.ActionListener; // imported, callbacks are lambdas below

public class Opponent { // one AI enemy with states, timers, sprites, and simple logic
    public enum opponentState { // all modes the opponent can be in
        IDLE, DODGING_LEFT, DODGING_RIGHT, BLOCKING,
        ATTACKING_LEFT, ATTACKING_RIGHT, STUNNED,
        TAUNTING, SUPERPUNCHING, WINDINGL, WINDINGR, HURT, DEAD
    }

    private final int RETURN_SPEED_SLOW = 4; // how fast it slides back to base
    private final int HURT_KNOCKBACK = 30; // defined but not used here
    private long invincible_duration = 4000; // ms of invincibility after many hits
    private boolean is_invincible = false; // currently invincible flag
    private boolean isPaused = false; // game pause flag for this opponent
    private long inviRemainingTime = 0; // saved remaining ms for invincibility timer on pause
    private long recoveryRemainingTime = 0; // saved remaining ms for recovery timer on pause
    private long windupRemainingTime = 0; // saved remaining ms for windup timer on pause
    private long stunRemainingTime = 0; // saved remaining ms for stun timer on pause
    private boolean isSuperPunching = false; // if current windup is for a super punch
    private long inviTimerStartTime = 0; // start times to compute remaining on pause
    private long recoveryTimerStartTime = 0; // same for recovery
    private long windupTimerStartTime = 0; // same for windup
    private long stunTimerStartTime = 0; // same for stun
    private Timer windupTimer = null; // delayed switch from windup -> attack
    private Timer recoveryTimer = null; // brief lock after defend/dodge/taunt
    private Timer inviTimer = null; // temporary invincibility window
    private Timer stunTimer = null; // stunned duration

    private Image oIDLE, oLEFTPUNCH, oRIGHTPUNCH, oLEFTDODGE, oRIGHTDODGE; // sprites
    private Image oWINDUPL, oWINDUPR, oBLOCK, oHURT1, oHURT2, oTAUNT, oSTUN; // more sprites
    private String spriteFolder; // which assets set to load

    private int x, y, baseX, baseY; // position and the home position
    private int width = 350, height = 500; // draw size
    private double health, maxHealth, damage; // stats
    private int speed; // lower = slower actions; also scales some timers
    private opponentState currentOpponentState = opponentState.IDLE; // current mode
    private boolean isWindingUp = false; // windup in progress flag
    private boolean isRecovering = false; // recovery in progress flag
    private int isHitCounter = 0; // counts recent hits to trigger invincibility
    private int aiTimer = 0; // counts frames till next decision
    private int aiDelay; // frames to wait before deciding an action
    private int punchTimer = 0; // counts frames of attack animation
    private Player player; // reference (not used for decisions here)

    public Opponent(String spriteFolder, int startX, int startY, double maxHealth, double damage, int speed, Player player){ // set up stats and sprites
        this.spriteFolder = spriteFolder; // which folder of images to use
        this.x = this.baseX = startX; // position
        this.y = this.baseY = startY; // position
        this.maxHealth = maxHealth; // stat
        this.health = maxHealth; // current health
        this.damage = damage; // damage per attack
        this.speed = speed; // affects timing
        this.player = player; // stored reference
        this.aiDelay = speed * 120; // slower speed -> longer delay between actions
        loadSprites(); // read images
    }

    public void rightPunch(){ // plan a punch from left windup side
        if(currentOpponentState == opponentState.STUNNED) return; // cannot act while stunned
        currentOpponentState = opponentState.WINDINGL; // start windup (left means will hit right)
    }
    public void leftPunch(){ // plan a punch from right windup side
        if(currentOpponentState == opponentState.STUNNED) return; // cannot act while stunned
        currentOpponentState = opponentState.WINDINGR; // start windup (right means will hit left)
    }
    public void rightDodge(){ // set dodge right
        currentOpponentState = opponentState.DODGING_RIGHT;
    }
    public void leftDodge(){ // set dodge left
        currentOpponentState = opponentState.DODGING_LEFT;
    }
    public void block(){ // set blocking
        currentOpponentState = opponentState.BLOCKING;
    }
    public void Taunt(){ // set taunting
        currentOpponentState = opponentState.TAUNTING;
    }
    public void startSuperPunchWindup() { // begin a super punch windup
        if(currentOpponentState == opponentState.STUNNED) return; // cannot act while stunned
        isSuperPunching = true; // mark as super
        if (Math.random() < 0.5) { // randomly choose windup side
            currentOpponentState = opponentState.WINDINGL;
        } else {
            currentOpponentState = opponentState.WINDINGR;
        }
    }

    public void stun(int durationMillis) { // put the opponent into stunned state for a duration
        if (currentOpponentState == opponentState.STUNNED || currentOpponentState == opponentState.DEAD) return; // ignore if already stunned/dead
        cancelTimers(); // stop ongoing phases
        currentOpponentState = opponentState.STUNNED; // become stunned
        stunTimerStartTime = System.currentTimeMillis(); // remember start
        stunTimer = new Timer(durationMillis, e -> { // after duration, return to idle (event-driven)
            currentOpponentState = opponentState.IDLE;
            ((Timer)e.getSource()).stop();
            stunTimer = null;
            stunTimerStartTime = 0;
        });
        stunTimer.setRepeats(false); // one-shot
        stunTimer.start(); // start timer
    }

    public void takeDamage(double amount) { // reduce health depending on current state
        if (currentOpponentState == opponentState.DODGING_LEFT || currentOpponentState == opponentState.DODGING_RIGHT) return; // dodging avoids damage
        if (isSuperPunchWindup()) { // super windup has armor
            return;
        }
        double appliedDamage = amount; // start with given damage
        isSuperPunching = false; // any hit cancels super intent
        if (is_invincible) { // temporary invincibility halves damage
            appliedDamage *= 0.5;
            this.health -= appliedDamage;
            return;
        }
        if (isWindingUp) { // interrupt windup
            cancelTimers();
        }
        if (currentOpponentState == opponentState.BLOCKING) { // blocking reduces to 5%
            appliedDamage *= 0.05;
            this.health -= appliedDamage;
            return;
        }
        currentOpponentState = opponentState.HURT; // enter hurt state
        hitCounter(); // track repeated hits
        this.health -= appliedDamage; // subtract health
        if (this.health <= 0) { // if dead
            this.health = 0;
            currentOpponentState = opponentState.DEAD;
            cancelTimers(); // stop all timers
        }
    }

    public void hitCounter(){ // count hits; after many hits, become invincible briefly
        isHitCounter++;
        if (isHitCounter > 4){ // threshold
            activateInvincibility();
        }
    }

    public void resetHitCounter(){ // clear hit count
        isHitCounter = 0;
    }

    public void activateInvincibility(){ // start temporary invincibility
        if (is_invincible) return; // already active
        is_invincible = true; // set flag
        resetHitCounter(); // reset hits
        inviTimerStartTime = System.currentTimeMillis(); // remember start time
        inviTimer = new Timer((int)invincible_duration, e -> { // after duration, turn off (event-driven)
            is_invincible = false;
            ((Timer)e.getSource()).stop();
            inviTimer = null;
            inviTimerStartTime = 0;
        });
        inviTimer.setRepeats(false); // one-shot
        inviTimer.start(); // start timer
    }

    private void startRecovery(int durationMillis) { // short lockout before next action
        if (isRecovering) return; // already in recovery
        isRecovering = true; // set flag
        recoveryTimerStartTime = System.currentTimeMillis(); // remember start
        recoveryTimer = new Timer(durationMillis, e -> { // after duration, go idle (event-driven)
            currentOpponentState = opponentState.IDLE;
            isRecovering = false;
            ((Timer)e.getSource()).stop();
            recoveryTimer = null;
            recoveryTimerStartTime = 0;
        });
        recoveryTimer.setRepeats(false); // one-shot
        recoveryTimer.start(); // start
    }

    private void decideNextAction() { // simple random AI to pick next move
        double rand = Math.random(); // random 0..1
        if (is_invincible) { // during invincibility: mostly attack, sometimes block
            if (rand < 0.80) {
                if (Math.random() < 0.5) rightPunch();
                else leftPunch();
            } else {
                currentOpponentState = opponentState.BLOCKING;
                startRecovery(speed * 800);
            }
            return;
        }
        if (isHitCounter >= 3) { // if getting hit a lot: defend more
            if (rand < 0.50) {
                currentOpponentState = opponentState.BLOCKING;
                startRecovery(speed * 1500);
            } else if (rand < 0.75) {
                currentOpponentState = (Math.random() < 0.5) ? opponentState.DODGING_LEFT : opponentState.DODGING_RIGHT;
                startRecovery((int)(speed * 800));
            } else {
                if (Math.random() < 0.5) rightPunch();
                else leftPunch();
            }
            return;
        }
        if (rand < 0.25) { // 25%: super windup
            startSuperPunchWindup();
        } else if (rand < 0.65) { // next 40%: normal punch
            if (Math.random() < 0.5) rightPunch();
            else leftPunch();
        } else if (rand < 0.85) { // next 20%: block
            currentOpponentState = opponentState.BLOCKING;
            startRecovery(speed * 1000);
        } else { // last 15%: taunt or dodge
            if (Math.random() < 0.5) {
                currentOpponentState = opponentState.TAUNTING;
                startRecovery(speed * 1000);
            } else {
                currentOpponentState = (Math.random() < 0.5)
                        ? opponentState.DODGING_LEFT
                        : opponentState.DODGING_RIGHT;
                startRecovery((int)(speed * 800));
            }
        }
    }

    private void cancelTimers() { // stop and clear all timers and phase flags
        if (windupTimer != null) {
            windupTimer.stop();
            windupTimer = null;
        }
        if (recoveryTimer != null) {
            recoveryTimer.stop();
            recoveryTimer = null;
        }
        if (inviTimer != null) {
            inviTimer.stop();
            inviTimer = null;
        }
        if (stunTimer != null) {
            stunTimer.stop();
            stunTimer = null;
        }
        isWindingUp = false; // clear flags
        isRecovering = false;
        isSuperPunching = false;
    }

    public void update() { // called every frame by the game loop
        if (isPaused) return; // no updates while paused
        aiTimer++; // count frames
        if (currentOpponentState == opponentState.IDLE && aiTimer >= aiDelay) { // time to pick next move
            decideNextAction();
            aiTimer = 0;
        }
        if (currentOpponentState == opponentState.DEAD || currentOpponentState == opponentState.STUNNED) {
            return; // no active behavior while dead or stunned
        }
        if ((currentOpponentState == opponentState.WINDINGL || currentOpponentState == opponentState.WINDINGR) && !isWindingUp) { // start windup once
            isWindingUp = true;
            windupTimerStartTime = System.currentTimeMillis();
            int duration = isSuperPunching ? (speed * 2000) : (speed * 1000); // super has longer windup
            opponentState finalState = (currentOpponentState == opponentState.WINDINGL)
                    ? opponentState.ATTACKING_RIGHT
                    : opponentState.ATTACKING_LEFT; // which side the punch will be
            windupTimer = new Timer(duration, e -> { // after windup, briefly mark super then attack (event-driven)
                if (isSuperPunching) {
                    currentOpponentState = opponentState.SUPERPUNCHING; // flash super
                } else {
                    currentOpponentState = finalState; // normal attack
                }
                Timer attackDelay = new Timer(10, e2 -> { // tiny delay to convert SUPERPUNCHING -> ATTACKING
                    if (currentOpponentState == opponentState.SUPERPUNCHING) {
                        currentOpponentState = finalState;
                    }
                    ((Timer)e2.getSource()).stop();
                });
                attackDelay.setRepeats(false);
                attackDelay.start();
                isWindingUp = false; // clear flags
                isSuperPunching = false;
                ((Timer)e.getSource()).stop();
                windupTimerStartTime = 0;
            });
            windupTimer.setRepeats(false);
            windupTimer.start();
        }
        if (currentOpponentState == opponentState.HURT && !isRecovering) { // auto recover from hurt
            isRecovering = true;
            Timer hurtTimer = new Timer(500, e -> { // after 0.5s go idle (event-driven)
                if (currentOpponentState == opponentState.HURT)
                    currentOpponentState = opponentState.IDLE;
                isRecovering = false;
                ((Timer)e.getSource()).stop();
            });
            hurtTimer.setRepeats(false);
            hurtTimer.start();
        }
        if (currentOpponentState == opponentState.ATTACKING_LEFT || currentOpponentState == opponentState.ATTACKING_RIGHT) { // keep attack for a short time
            punchTimer++;
            if (punchTimer >= 30) { // then end attack
                currentOpponentState = opponentState.IDLE;
                punchTimer = 0;
            }
        } else {
            punchTimer = 0; // reset counter otherwise
        }
        if (currentOpponentState != opponentState.WINDINGL && currentOpponentState != opponentState.WINDINGR && currentOpponentState != opponentState.DODGING_LEFT && currentOpponentState != opponentState.DODGING_RIGHT) { // only drift back in neutral-like states
            if (Math.abs(x - baseX) > RETURN_SPEED_SLOW) {
                x += (x < baseX) ? RETURN_SPEED_SLOW : -RETURN_SPEED_SLOW; // slide back horizontally
            } else {
                x = baseX;
            }
            if (Math.abs(y - baseY) > RETURN_SPEED_SLOW) {
                y += (y < baseY) ? RETURN_SPEED_SLOW : -RETURN_SPEED_SLOW; // slide back vertically
            } else {
                y = baseY;
            }
        }
    }

    private void loadSprites() { // read and scale all needed images
        try {
            oIDLE = new ImageIcon("assets/" + spriteFolder + "/opp_idle.png").getImage().getScaledInstance(width, height, Image.SCALE_SMOOTH);
            oLEFTPUNCH = new ImageIcon("assets/" + spriteFolder + "/opp_punchL.png").getImage().getScaledInstance(width, height, Image.SCALE_SMOOTH);
            oRIGHTPUNCH = new ImageIcon("assets/" + spriteFolder + "/opp_punchR.png").getImage().getScaledInstance(width, height, Image.SCALE_SMOOTH);
            oLEFTDODGE = new ImageIcon("assets/" + spriteFolder + "/opp_dodgeL.png").getImage().getScaledInstance(width, height, Image.SCALE_SMOOTH);
            oRIGHTDODGE = new ImageIcon("assets/" + spriteFolder + "/opp_dodgeR.png").getImage().getScaledInstance(width, height, Image.SCALE_SMOOTH);
            oWINDUPL = new ImageIcon("assets/" + spriteFolder + "/opp_windupL.png").getImage().getScaledInstance(width, height, Image.SCALE_SMOOTH);
            oWINDUPR = new ImageIcon("assets/" + spriteFolder + "/opp_windupR.png").getImage().getScaledInstance(width, height, Image.SCALE_SMOOTH);
            oBLOCK = new ImageIcon("assets/" + spriteFolder + "/opp_block.png").getImage().getScaledInstance(width, height, Image.SCALE_SMOOTH);
            oHURT1 = new ImageIcon("assets/" + spriteFolder + "/opp_hurt1.png").getImage().getScaledInstance(width, height, Image.SCALE_SMOOTH);
            oHURT2 = new ImageIcon("assets/" + spriteFolder + "/opp_hurt2.png").getImage().getScaledInstance(width, height, Image.SCALE_SMOOTH);
            oTAUNT = new ImageIcon("assets/" + spriteFolder + "/opp_taunt.png").getImage().getScaledInstance(width, height, Image.SCALE_SMOOTH);
            oSTUN = new ImageIcon("assets/" + spriteFolder + "/opp_stun.png").getImage().getScaledInstance(width, height, Image.SCALE_SMOOTH);
        } catch (Exception e) {
            System.out.println("Failed to load opponent sprites from folder: " + spriteFolder + " (" + e.getMessage() + ")");
        }
    }

    public boolean isSuperPunchWindup() { // true if currently in the armored super windup
        return isSuperPunching && (currentOpponentState == opponentState.WINDINGL || currentOpponentState == opponentState.WINDINGR);
    }

    public void draw(Graphics g){ // render the opponent and its health bar
        Image currentSprite = oIDLE; // pick sprite by state
        switch (currentOpponentState) {
            case ATTACKING_LEFT: currentSprite = oLEFTPUNCH; break;
            case ATTACKING_RIGHT: currentSprite = oRIGHTPUNCH; break;
            case DODGING_LEFT: currentSprite = oLEFTDODGE; break;
            case DODGING_RIGHT: currentSprite = oRIGHTDODGE; break;
            case BLOCKING: currentSprite = oBLOCK; break;
            case STUNNED: currentSprite = oSTUN; break;
            case TAUNTING: currentSprite = oTAUNT; break;
            case SUPERPUNCHING:
            case WINDINGL: currentSprite = oWINDUPL; break; // left windup/super
            case WINDINGR: currentSprite = oWINDUPR; break; // right windup/super
            case HURT: currentSprite = oHURT1; break;
            case DEAD: currentSprite = oSTUN; break; // reuse stun sprite
            default: currentSprite = oIDLE; break;
        }
        int drawX = x; // start at current position
        int drawY = y;
        if (currentOpponentState == opponentState.HURT) { // tiny vertical shake when hurt
            drawY += (int)(Math.random() * 4 - 2);
        }
        if (currentOpponentState == opponentState.STUNNED) { // wobble when stunned
            double t = System.currentTimeMillis() / 250.0;
            int sway = (int)(Math.sin(t) * 15);
            drawX += sway;
            drawY += Math.abs(sway / 3);
        }
        if (!isSuperPunching) { // small lean during normal windup
            if (currentOpponentState == opponentState.WINDINGR) {
                drawY -= 10;
                drawX -= 30;
            } else if (currentOpponentState == opponentState.WINDINGL) {
                drawY -= 10;
                drawX += 30;
            }
        }
        if (isSuperPunchWindup()) { // super windup: shake around base and show shrinking circle
            drawX = baseX + (int)(Math.random() * 8 - 4);
            drawY = baseY + (int)(Math.random() * 6 - 3);
            
            // Calculate circle size based on remaining windup time
            if (windupTimerStartTime > 0) { // safety check: ensure windup has started
                long currentTime = System.currentTimeMillis();
                long elapsed = currentTime - windupTimerStartTime;
                int totalDuration = speed * 2000; // super punch windup duration
                double progress = Math.max(0.0, 1.0 - ((double)elapsed / totalDuration)); // 1.0 = start, 0.0 = about to punch
                
                // Circle starts at max size and shrinks to 0
                int maxRadius = 400; // maximum circle radius
                int circleRadius = (int)(maxRadius * progress);
                
                if (circleRadius > 0) { // only draw if circle is visible
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    
                    // Red circle with transparency (fades as it shrinks)
                    int alpha = (int)(100 + (progress * 155)); // fade as it shrinks
                    g2.setColor(new Color(255, 0, 0, Math.min(255, alpha)));
                    
                    // Center circle on opponent
                    int centerX = baseX + width / 2;
                    int centerY = baseY + height / 2;
                    int circleX = centerX - circleRadius;
                    int circleY = centerY - circleRadius;
                    
                    g2.fillOval(circleX, circleY, circleRadius * 2, circleRadius * 2);
                    g2.dispose();
                }
            }
        }
        g.drawImage(currentSprite, drawX, drawY, width, height, null); // draw sprite
        g.setColor(Color.RED); // health bar fill
        int barWidth = (int) ((health / maxHealth) * 200);
        g.fillRect(265, 20, barWidth, 20);
        g.setColor(Color.WHITE); // health bar border
        g.drawRect(265, 20, 200, 20);
    }

    public void pause() { // pause-aware: capture remaining times and stop timers
        if (isPaused) return;
        isPaused = true;
        if (inviTimer != null && inviTimer.isRunning()) {
            long elapsed = System.currentTimeMillis() - inviTimerStartTime;
            inviRemainingTime = Math.max(0, inviTimer.getDelay() - elapsed);
            inviTimer.stop();
        }
        if (recoveryTimer != null && recoveryTimer.isRunning()) {
            long elapsed = System.currentTimeMillis() - recoveryTimerStartTime;
            recoveryRemainingTime = Math.max(0, recoveryTimer.getDelay() - elapsed);
            recoveryTimer.stop();
        }
        if (windupTimer != null && windupTimer.isRunning()) {
            long elapsed = System.currentTimeMillis() - windupTimerStartTime;
            windupRemainingTime = Math.max(0, windupTimer.getDelay() - elapsed);
            windupTimer.stop();
        }
        if (stunTimer != null && stunTimer.isRunning()) {
            long elapsed = System.currentTimeMillis() - stunTimerStartTime;
            stunRemainingTime = Math.max(0, stunTimer.getDelay() - elapsed);
            stunTimer.stop();
        }
    }

    public void resume() { // rebuild timers from remaining times when unpausing
        if (!isPaused) return;
        isPaused = false;
        if (inviRemainingTime > 0) {
            inviTimer = new Timer((int)inviRemainingTime, e -> {
                is_invincible = false;
                ((Timer)e.getSource()).stop();
                inviTimer = null;
                inviRemainingTime = 0;
            });
            inviTimer.setRepeats(false);
            inviTimer.start();
            inviTimerStartTime = System.currentTimeMillis() - (inviTimer.getDelay() - inviRemainingTime);
        }
        if (recoveryRemainingTime > 0) {
            recoveryTimer = new Timer((int)recoveryRemainingTime, e -> {
                currentOpponentState = opponentState.IDLE;
                isRecovering = false;
                ((Timer)e.getSource()).stop();
                recoveryTimer = null;
                recoveryRemainingTime = 0;
            });
            recoveryTimer.setRepeats(false);
            recoveryTimer.start();
            recoveryTimerStartTime = System.currentTimeMillis() - (recoveryTimer.getDelay() - recoveryRemainingTime);
        }
        if (windupRemainingTime > 0) {
            opponentState nextState = (currentOpponentState == opponentState.WINDINGL)
                    ? opponentState.ATTACKING_RIGHT : opponentState.ATTACKING_LEFT; // decide next attack side
            windupTimer = new Timer((int)windupRemainingTime, e -> {
                currentOpponentState = nextState;
                isWindingUp = false;
                ((Timer)e.getSource()).stop();
                windupRemainingTime = 0;
            });
            windupTimer.setRepeats(false);
            windupTimer.start();
            windupTimerStartTime = System.currentTimeMillis() - (windupTimer.getDelay() - windupRemainingTime);
        }
        if (stunRemainingTime > 0) {
            stunTimer = new Timer((int)stunRemainingTime, e -> {
                currentOpponentState = opponentState.IDLE;
                ((Timer)e.getSource()).stop();
                stunTimer = null;
                stunRemainingTime = 0;
            });
            stunTimer.setRepeats(false);
            stunTimer.start();
            stunTimerStartTime = System.currentTimeMillis() - (stunTimer.getDelay() - stunRemainingTime);
        }
    }

    public void resetOnPlayerDeath() { // restore to fresh state when the player respawns
        cancelTimers(); // clear timers and flags
        this.health = this.maxHealth; // full health
        this.currentOpponentState = opponentState.IDLE; // idle
        this.is_invincible = false; // no invincibility
        this.isWindingUp = false; // clear windup
        this.isRecovering = false; // clear recovery
        this.isPaused = false; // unpaused
        this.isSuperPunching = false; // not supering
        this.isHitCounter = 0; // clear hit streak
        this.aiTimer = 0; // reset AI timer
        this.punchTimer = 0; // reset attack timer
        this.x = this.baseX; // back to base position
        this.y = this.baseY; // back to base position
    }

    public void setCurrentState(opponentState newState) { currentOpponentState = newState; } // direct state setter (used by game panel)
    public double getHealth() { return health; } // current health
    public double getMaxHealth() { return maxHealth; } // maximum health
    public double getSpeed() { return speed; } // speed stat
    public opponentState getCurrentState() { return currentOpponentState; } // current state
    public double getDamage(){return damage;} // base damage per hit
}
