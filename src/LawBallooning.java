import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

import javax.imageio.ImageIO;

import org.rsbot.bot.input.CanvasWrapper;
import org.rsbot.event.events.ServerMessageEvent;
import org.rsbot.event.listeners.PaintListener;
import org.rsbot.event.listeners.ServerMessageListener;
import org.rsbot.script.Calculations;
import org.rsbot.script.Constants;
import org.rsbot.script.Script;
import org.rsbot.script.ScriptManifest;
import org.rsbot.script.wrappers.RSArea;
import org.rsbot.script.wrappers.RSTile;

@ScriptManifest(authors = { "Allometry" }, category = "!RC", name = "Law Ballooning", version = 1.0, description = "")
public class LawBallooning extends Script implements PaintListener, ServerMessageListener {	
	//Areas
	private RSArea alterArea = new RSArea(new RSTile(2448,4818), new RSTile(2479, 4847));
	private RSArea castleWarsArea = new RSArea(new RSTile(2430, 3073), new RSTile(2455, 3106));
	private RSArea entranaArea = new RSArea(new RSTile(2802, 3329), new RSTile(2869, 3391));
	
	//Counters and Integers
	private float lawRuneXP = 9.5f;
	private int lawRunesCrafted = 0;
	private int ringDuellingCharges = 0;
	private int startingLevel = 0;
	private int startingXP = 0;
	private long startingTime = 0;
	private long timeoutMillis;
	
	//Flags
	private boolean isVerbose = true;
	private boolean isScriptLoaded = false;
	
	//Formatters
	private NumberFormat numberFormatter = NumberFormat.getNumberInstance(Locale.US);
	
	//Images
	private Image cursorImage;
	private Image lawRuneImage, tagOrange, sumImage;
	private Image timeImage, lawRuneToGoImage;
	private Image arrowRotateClockwise;
	
	//Image Observers
	private ImageObserver observer;
	
	//Interface IDs
	private int ballonTravelMapParentID = 469;
	private int ballonTravelMapEntranaChildID = 17;
	private int hastleWeaponsParentID = 242;
	private int hastleWeaponsChildID = 6;
	private int searchWeaponsParentID = 210;
	private int searchWeaponsChildID = 2;
	private int travelingMapParentID = 120;
	
	//Inventory IDs
	private int hugePouchID = -1, largePouchID = 5512, mediumPouchID = 5510, smallPouchID = 5509;
	private int normalLogID = 1511;
	private int lawRuneID = 563, pureEssenceID = 7936;
	private int ringDueling8ID = 2552, ringDueling7ID = 2554, ringDueling6ID = 2556, ringDueling5ID = 2558;
	private int ringDueling4ID = 2560, ringDueling3ID = 2562, ringDueling2ID = 2564, ringDueling1ID = 2566;
	
	//NPC IDs
	private int assistantMarrowID = 5063;
	
	//Object IDs
	private int lawRuneAlterID = 2485;
	private int mysteriousRuinsID = 2459;
	
	//Paths
	private RSTile[] cwlbPath = {new RSTile(2444, 3083), new RSTile(2444, 3087), new RSTile(2448, 3089), new RSTile(2454, 3089), new RSTile(2458, 3092), new RSTile(2458, 3097), new RSTile(2458, 3102), new RSTile(2458, 3107), new RSTile(2462, 3108)};
	private RSTile[] lbmrPath = {new RSTile(2808, 3354), new RSTile(2813, 3350), new RSTile(2819, 3344), new RSTile(2824, 3344), new RSTile(2829, 3343), new RSTile(2835, 3343), new RSTile(2838, 3347), new RSTile(2844, 3348), new RSTile(2850, 3348), new RSTile(2855, 3348), new RSTile(2859, 3351), new RSTile(2857, 3356), new RSTile(2857, 3361), new RSTile(2857, 3367), new RSTile(2857, 3372), new RSTile(2857, 3376), new RSTile(2858, 3379)};
	private RSTile[] mrlaPath = {new RSTile(2464, 4819), new RSTile(2464, 4824), new RSTile(2464, 4828), new RSTile(2464, 4830)};
	
	//Scoreboard
	private Scoreboard topLeftScoreboard, topRightScoreboard, bottomRightScoreboard;
	
	//Scoreboard Widgets
	private ScoreboardWidget runesCrafted, runesToLevel;
	private ScoreboardWidget currentRuntime, currentLevel, experiencedGained;
	private ScoreboardWidget status;
	
	//Scoreboard Widget Indexes
	private int runesCraftedIndex, runesToLevelIndex;
	private int currentRuntimeIndex, currentLevelIndex, experiencedGainedIndex;
	private int statusIndex;
	
	//States
	private enum State {
		setup,
		broken,
		waiting,
		resting,
		running,
		walkToBank,
		openBank,
		closeBank,
		storeCraftedRunes,
		retrieveRing,
		equipRing,
		retrieveNormalLog,
		retrieveRuneEssence,
		fillPouches,
		walkToBalloon,
		walkToAssistant,
		talkToAssistant,
		getHastledByAssistant,
		getSearchedByAssistant,
		travelToEntrana,
		walkToMysteriousTile,
		walkToRuins,
		enterRuins,
		walkToAlter,
		craftRunes,
		teleportToBank
	}
	private State state = State.setup;
	
	//Strings
	private String assistantMarrowAction = "Fly Assistant Marrow";
	private String balloonTravelMapEntranaAction = "Entrana";
	private String equipRingDuelling = "Wear Ring of duelling";
	private String lawRuneAlterAction = "Craft";
	private String mysteriousRuinsAction = "Enter Mysterious ruins";
	
	//String Maps
	private HashMap<String, String> stateConversion = new HashMap<String, String>();
	
	//Tiles
	private RSTile alterTile = new RSTile(2464, 4831);
	private RSTile bankChestTile = new RSTile(2443, 3083);
	private RSTile mysteriousRuinsTile = new RSTile(2858, 3379);
	
	@Override
	public boolean onStart(Map<String,String> args) {
		//Assemble String Map
		stateConversion.put("setup", "Loading...");
		stateConversion.put("broken", "Encountered a serious error!");
		stateConversion.put("waiting", "Waiting for task to finish.");
		stateConversion.put("resting", "Out of breath; resting...");
		stateConversion.put("running", "Okay, we're ready to run again!");
		stateConversion.put("walkToBank", "Walking to C.W. bank chest.");
		stateConversion.put("openBank", "Opening C.W. bank chest.");
		stateConversion.put("closeBank", "Closing C.W. bank chest.");
		stateConversion.put("storeCraftedRunes", "Depositing Law Runes.");
		stateConversion.put("retrieveRing", "Withdrawing Ring of Duelling.");
		stateConversion.put("equipRing", "Equipping Ring of Duelling.");
		stateConversion.put("retrieveNormalLog", "Withdrawing a Normal Log.");
		stateConversion.put("retrieveRuneEssence", "Withdrawing Pure Rune Essence.");
		stateConversion.put("fillPouches", "Filling Pouches.");
		stateConversion.put("walkToBalloon", "Walking to C.W. balloon.");
		stateConversion.put("walkToAssistant", "Walking to balloon assistant.");
		stateConversion.put("talkToAssistant", "Trying to have a conversation...");
		stateConversion.put("getHastledByAssistant", "Great, we're getting hastled!");
		stateConversion.put("getSearchedByAssistant", "Fine, Search! What privacy?");
		stateConversion.put("travelToEntrana", "And we're flying!");
		stateConversion.put("walkToMysteriousTile", "Walking up to mysterious ruins.");
		stateConversion.put("walkToRuins", "Walking to mysterious ruins.");
		stateConversion.put("enterRuins", "Climbing down this mess.");
		stateConversion.put("walkToAlter", "Walking up to crafting alter.");
		stateConversion.put("craftRunes", "Craftin' some law runes.");
		stateConversion.put("teleportToBank", "Teleporting to Castle Wars...");
		
		//Load Images
		try {
			cursorImage = ImageIO.read(new URL("http://scripts.allometry.com/app/webroot/img/cursors/cursor-01.png"));
			lawRuneImage = ImageIO.read(new URL("http://scripts.allometry.com/icons/law_rune.png"));
			timeImage = ImageIO.read(new URL("http://scripts.allometry.com/icons/time.png"));
			lawRuneToGoImage = ImageIO.read(new URL("http://scripts.allometry.com/icons/law_rune_go.png"));
			tagOrange = ImageIO.read(new URL("http://scripts.allometry.com/icons/tag_orange.png"));
			sumImage = ImageIO.read(new URL("http://scripts.allometry.com/icons/sum.png"));
			arrowRotateClockwise = ImageIO.read(new URL("http://scripts.allometry.com/icons/arrow_rotate_clockwise.png"));
		} catch(Exception e) {}
		
		//Top Left Scoreboard
		runesCrafted = new ScoreboardWidget(lawRuneImage, "");
		runesToLevel = new ScoreboardWidget(lawRuneToGoImage, "");
		
		topLeftScoreboard = new Scoreboard(Scoreboard.TOP_LEFT, 128, 5);
		topLeftScoreboard.addWidget(runesCrafted);
		topLeftScoreboard.addWidget(runesToLevel);
		
		runesCraftedIndex = 0;
		runesToLevelIndex = 1;
		
		//Top Right Scoreboard
		currentRuntime = new ScoreboardWidget(timeImage, "");
		currentLevel = new ScoreboardWidget(tagOrange, "");
		experiencedGained = new ScoreboardWidget(sumImage, "");
		
		topRightScoreboard = new Scoreboard(Scoreboard.TOP_RIGHT, 128, 5);
		topRightScoreboard.addWidget(currentRuntime);
		topRightScoreboard.addWidget(currentLevel);
		topRightScoreboard.addWidget(experiencedGained);
		
		currentRuntimeIndex = 0;
		currentLevelIndex = 1;
		experiencedGainedIndex = 2;
		
		//Bottom Right Scoreboard
		status = new ScoreboardWidget(arrowRotateClockwise, "");
		
		bottomRightScoreboard = new Scoreboard(Scoreboard.BOTTOM_RIGHT, 256, 5);
		bottomRightScoreboard.addWidget(status);
		
		statusIndex = 0;
		
		if(equipmentContainsRingDuelling())
			ringDuellingCharges = getRingDuellingCharges();
		
		startingLevel = skills.getCurrSkillLevel(STAT_RUNECRAFTING);
		startingTime = System.currentTimeMillis();
		startingXP = skills.getCurrentSkillExp(STAT_RUNECRAFTING);
		
		isScriptLoaded = true;
		
		return true;
	}
	
	@Override
	public int loop() {
		try {
			this.updateState();
			
			switch(state) {
				case resting:
					rest(100);
				break;
				
				case running:
					setRun(true);
					if(!isRunning())
						wait(random(1250, 1750));
				break;
				
				case walkToBank:
					walkTileMM(bankChestTile);
					wait(random(1250, 1750));
				break;
				
				case openBank:
					setTimeout(5);
					do {
						bank.open(true);
						if(!bank.isOpen())
							wait(random(1250, 1750));
					} while(!bank.isOpen() && !isTimedOut());
				break;
				
				case closeBank:
					bank.close();
					
					setTimeout(3);
					while(bank.isOpen() && !isTimedOut())
						verbose("Waiting to close bank...");
				break;
				
				case storeCraftedRunes:
					bank.deposit(lawRuneID, 0);
					
					setTimeout(3);
					while(inventoryContains(lawRuneID) && !isTimedOut())
						verbose("Waiting to store law runes...");
				break;
				
				case retrieveRing:
					if(!inventoryContains(ringDueling8ID))
						bank.withdraw(ringDueling8ID, 1);
					
					setTimeout(3);
					while(!inventoryContains(ringDueling8ID) && !isTimedOut())
						verbose("Waiting for ring to withdraw...");
				break;
				
				case equipRing:
					atInventoryItem(ringDueling8ID, equipRingDuelling);
					
					setTimeout(3);
					while(inventoryContains(ringDueling8ID) && !isTimedOut())
						verbose("Waiting for ring to be equipped...");
					
					if(inventoryContains(ringDueling8ID))
						ringDuellingCharges = 8;
				break;
				
				case retrieveNormalLog:
					bank.withdraw(normalLogID, 1);
					
					setTimeout(3);
					while(!inventoryContains(normalLogID) && !isTimedOut())
						verbose("Waiting for log to withdraw...");
				break;
				
				case retrieveRuneEssence:
					bank.withdraw(pureEssenceID, 0);
					
					setTimeout(3);
					while(!isInventoryFull() && !isTimedOut())
						verbose("Waiting for pure essence to withdraw...");
				break;
				
				case fillPouches:
					//Broken
				break;
				
				case walkToBalloon:
					walkPathMM(cwlbPath, 12);
					wait(random(1250, 1750));
				break;
				
				case walkToAssistant:
					walkTileMM(getNearestNPCByID(assistantMarrowID).getLocation());
					wait(random(1250, 1750));
				break;
				
				case talkToAssistant:
					turnToCharacter(getNearestNPCByID(assistantMarrowID));
					atNPC(getNearestNPCByID(assistantMarrowID), assistantMarrowAction);
					wait(random(1250, 1750));
				break;
				
				case getHastledByAssistant:
					atInterface(getInterface(hastleWeaponsParentID, hastleWeaponsChildID), "");
					wait(random(1250, 1750));
				break;
				
				case getSearchedByAssistant:
					atInterface(getInterface(searchWeaponsParentID, searchWeaponsChildID), "");
					wait(random(1250, 1750));
				break;
				
				case travelToEntrana:
					atInterface(getInterface(ballonTravelMapParentID, ballonTravelMapEntranaChildID), balloonTravelMapEntranaAction);
					wait(random(1250, 1750));
				break;
				
				case walkToMysteriousTile:
					walkTo(mysteriousRuinsTile);
					wait(random(1250, 1750));
				break;
				
				case walkToRuins:
					walkPathMM(lbmrPath, 12);
					wait(random(1250, 1750));
				break;
				
				case enterRuins:
					atObject(getNearestObjectByID(mysteriousRuinsID), mysteriousRuinsAction);
					wait(random(1250, 1750));
				break;
				
				case walkToAlter:
					walkPathMM(mrlaPath, 12);
					wait(random(1250, 1750));
				break;
				
				case craftRunes:
					atObject(getNearestObjectByID(lawRuneAlterID), lawRuneAlterAction);
					wait(random(1250, 1750));
				break;
				
				case teleportToBank:
					do {
						openTab(Constants.TAB_EQUIPMENT);
					} while(getCurrentTab() != Constants.TAB_EQUIPMENT);
					
					atRingDuelling("Castle Wars");
					wait(random(1250, 1750));
				break;
			}
		} catch(Exception e) {}
		
		return 1;
	}
	
	@Override
	public void serverMessageRecieved(ServerMessageEvent message) {
		if(message.getMessage().contains("crumbles to dust")) {
			ringDuellingCharges = 0;
		} else if(message.getMessage().contains("bind")) {
			lawRunesCrafted += getInventoryCount(true);
		}
			
	}
	
	@Override
	public void onRepaint(Graphics g2) {		
		Graphics2D g = (Graphics2D)g2;
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		
		if(!isScriptLoaded) {
			Scoreboard loadingBoard = new Scoreboard(Scoreboard.BOTTOM_RIGHT, 128, 5);
			loadingBoard.addWidget(new ScoreboardWidget(timeImage, "Loading..."));
			loadingBoard.drawScoreboard(g);

			return ;
		}
		
		//Draw Custom Mouse Cursor
		g.drawImage(cursorImage, getMouseLocation().x - 16, getMouseLocation().y - 16, observer);
		
		//Draw Top Left Scoreboard
		topLeftScoreboard.getWidget(runesCraftedIndex).setWidgetText(numberFormatter.format(lawRunesCrafted));
		topLeftScoreboard.getWidget(runesToLevelIndex).setWidgetText("\u2248" + numberFormatter.format(Math.ceil(skills.getXPToNextLevel(STAT_RUNECRAFTING) / lawRuneXP)));
		topLeftScoreboard.drawScoreboard(g);
		
		//Draw Top Right Scoreboard
		topRightScoreboard.getWidget(currentRuntimeIndex).setWidgetText(millisToClock(System.currentTimeMillis() - startingTime));
		topRightScoreboard.getWidget(currentLevelIndex).setWidgetText("" + skills.getCurrSkillLevel(STAT_RUNECRAFTING));
		topRightScoreboard.getWidget(experiencedGainedIndex).setWidgetText(numberFormatter.format(skills.getCurrentSkillExp(STAT_RUNECRAFTING) - startingXP) + (((skills.getCurrSkillLevel(STAT_RUNECRAFTING) - startingLevel) == 0) ? "" : " (" + (skills.getCurrSkillLevel(STAT_RUNECRAFTING) - startingLevel) + ")"));
		topRightScoreboard.drawScoreboard(g);
		
		//Draw Bottom Right Scoreboard
		bottomRightScoreboard.getWidget(statusIndex).setWidgetText(stateConversion.get(state.name()));
		bottomRightScoreboard.drawScoreboard(g);
	}
	
	@Override
	public void onFinish() {}
	
	/**
	 * Formats millisecond time into HH:MM:SS
	 *
	 * @param milliseconds				milliseconds that should be converted into
	 * 									the HH:MM:SS format
	 * 									@see java.lang.System
	 * @return							formatted HH:MM:SS string
	 * @since 0.1
	 */
	private String millisToClock(long milliseconds) {
		long seconds = (milliseconds / 1000), minutes = 0, hours = 0;

		if (seconds >= 60) {
			minutes = (seconds / 60);
			seconds -= (minutes * 60);
		}

		if (minutes >= 60) {
			hours = (minutes / 60);
			minutes -= (hours * 60);
		}

		return (hours < 10 ? "0" + hours + ":" : hours + ":")
				+ (minutes < 10 ? "0" + minutes + ":" : minutes + ":")
				+ (seconds < 10 ? "0" + seconds : seconds);
	}
	
	public boolean atRingDuelling(String action) {
		if(equipmentContains(ringDueling8ID))
			return atEquippedItem(ringDueling8ID, action);
		else if(equipmentContains(ringDueling7ID))
			return atEquippedItem(ringDueling7ID, action);
		else if(equipmentContains(ringDueling6ID))
			return atEquippedItem(ringDueling6ID, action);
		else if(equipmentContains(ringDueling5ID))
			return atEquippedItem(ringDueling5ID, action);
		else if(equipmentContains(ringDueling4ID))
			return atEquippedItem(ringDueling4ID, action);
		else if(equipmentContains(ringDueling3ID))
			return atEquippedItem(ringDueling3ID, action);
		else if(equipmentContains(ringDueling2ID))
			return atEquippedItem(ringDueling2ID, action);
		else if(equipmentContains(ringDueling1ID))
			return atEquippedItem(ringDueling1ID, action);
		else
			return false;
	}
	
	public int getRingDuellingCharges() {
		if(equipmentContains(ringDueling8ID))
			return 8;
		else if(equipmentContains(ringDueling7ID))
			return 7;
		else if(equipmentContains(ringDueling6ID))
			return 6;
		else if(equipmentContains(ringDueling5ID))
			return 5;
		else if(equipmentContains(ringDueling4ID))
			return 4;
		else if(equipmentContains(ringDueling3ID))
			return 3;
		else if(equipmentContains(ringDueling2ID))
			return 2;
		else if(equipmentContains(ringDueling1ID))
			return 1;
		else
			return 0;
	}
	
	public boolean equipmentContainsRingDuelling() {
		return (equipmentContains(ringDueling8ID) || 
				equipmentContains(ringDueling7ID) || 
				equipmentContains(ringDueling6ID) || 
				equipmentContains(ringDueling5ID) || 
				equipmentContains(ringDueling4ID) || 
				equipmentContains(ringDueling3ID) || 
				equipmentContains(ringDueling2ID) ||
				equipmentContains(ringDueling1ID));
	}
	
	public boolean inventoryContainsRingDuelling() {
		return (inventoryContains(ringDueling8ID) || 
				inventoryContains(ringDueling7ID) || 
				inventoryContains(ringDueling6ID) || 
				inventoryContains(ringDueling5ID) || 
				inventoryContains(ringDueling4ID) || 
				inventoryContains(ringDueling3ID) || 
				inventoryContains(ringDueling2ID) ||
				inventoryContains(ringDueling1ID));
	}
	
	private boolean isTimedOut() {
		return (System.currentTimeMillis() > timeoutMillis);
	}
	
	private long setTimeout(int seconds) {
		return System.currentTimeMillis() + (seconds * 1000);
	}
	
	private void verbose(String message) {
		if(isVerbose) log(message);
	}
	
	public void updateState() {
		if(castleWarsArea.contains(getMyPlayer().getLocation())) {
			if(ringDuellingCharges <= 0) {
				if(inventoryContainsRingDuelling()) {
					if(bank.isOpen()) {
						state = State.closeBank;
					} else {
						state = State.equipRing;
					}
				} else {
					if(bank.isOpen()) {
						state = State.retrieveRing;
					} else {
						if(bankChestTile.distanceTo() < 3) {
							state = State.openBank;
						} else {
							state = State.walkToBank;
						}
					}
				}
			} else {
				if(inventoryContains(lawRuneID) && !inventoryContains(pureEssenceID)) {
					if(bank.isOpen()) {
						state = State.storeCraftedRunes;
					} else {
						if(bankChestTile.distanceTo() < 3) {
							state = State.openBank;
						} else {
							state = State.walkToBank;
						}
					}
				}
				
				if(inventoryEmptyExcept(hugePouchID, largePouchID, mediumPouchID, smallPouchID, normalLogID)) {
					if(bank.isOpen()) {
						if(inventoryContains(normalLogID)) {
							state = State.retrieveRuneEssence;
						} else {
							state = State.retrieveNormalLog;
						}
					} else {
						if(bankChestTile.distanceTo() < 3) {
							state = State.openBank;
						} else {
							state = State.walkToBank;
						}
					}
				}
				
				if(isInventoryFull()) {
					if(bank.isOpen()) {
						state = State.closeBank;
					} else {
						state = State.walkToBalloon;
					}
				}
			}
		} else if(entranaArea.contains(getMyPlayer().getLocation())) {
			if(Calculations.onScreen(mysteriousRuinsTile.getScreenLocation())) {
				if(mysteriousRuinsTile.distanceTo() > 4) {
					state = State.walkToMysteriousTile;
				} else {
					state = State.enterRuins;
				}
			} else {
				state = State.walkToRuins;
			}
		} else if(alterArea.contains(getMyPlayer().getLocation())) {
			if(Calculations.onScreen(alterTile.getScreenLocation())) {
				if(inventoryContains(pureEssenceID)) {
					state = State.craftRunes;
				} else {
					if(getMyPlayer().getAnimation() < 0) {
						state = State.teleportToBank;
					} else {
						state = State.waiting;
					}
				}
			} else {
				state = State.walkToAlter;
			}
		} else {
			if(isInventoryFull()) {
				if(getInterface(hastleWeaponsParentID).isValid()) {
					state = State.getHastledByAssistant;
				} else if(getInterface(searchWeaponsParentID).isValid()) {
					state = State.getSearchedByAssistant;
				} else if(getInterface(ballonTravelMapParentID).isValid()) {
					state = State.travelToEntrana;
				} else if(getInterface(travelingMapParentID).isValid()) {
					state = State.waiting;
				} else {
					if(getNearestNPCByID(assistantMarrowID) == null) {
						if(bank.isOpen()) {
							state = State.closeBank;
						} else {
							state = State.walkToBalloon;
						}
					} else {
						if(calculateDistance(getMyPlayer().getLocation(), getNearestNPCByID(assistantMarrowID).getLocation()) > 5)
							state = State.walkToAssistant;
						else
							state = State.talkToAssistant;
					}
				}
			}
		}
		
		if(!isRunning()) {
			if(getEnergy() < 80) {
				state = State.resting;
			} else {
				state = State.running;
			}
		}
	}
	
	/**
	 * Binoculars is a class for remote control of this bot. It is for use with
	 * the binoculars webapp on AlloNet.
	 * 
	 * @author allometry
	 * @version 1.0
	 * @since 1.0
	 */
	public class Binoculars {
		private double thumbnailWidth = 320, thumbnailHeight = 240;
		private String remoteKey = "ABCDEFG0123456789";
		
		public void Bionoculars(String remoteKey) {
			this.remoteKey = remoteKey;
		}
		
		public void tick() {
			BufferedImage thumbnail = getThumbnailScreenCapture();
			ByteArrayOutputStream ios = new ByteArrayOutputStream();
			
			try {
				ImageIO.write(thumbnail, "jpg", ios);
			} catch (IOException e) {
				e.printStackTrace();
			}	
		}
		
		public BufferedImage getThumbnailScreenCapture() {
			BufferedImage rawScreenCapture = CanvasWrapper.getBotBuffer();
			AffineTransform scaleTransformer = new AffineTransform();
			
			scaleTransformer.scale(thumbnailWidth, thumbnailHeight);
			
			AffineTransformOp scaleOperation = new AffineTransformOp(scaleTransformer, AffineTransformOp.TYPE_BILINEAR);
			
			return scaleOperation.filter(rawScreenCapture, null);
		}
	}
	
	public class SimpleHTTP {
		private Random random = new Random();
		private String boundary = "---------------------------" + randomString() + randomString() + randomString();
		
		protected Map cookies = null;
		protected OutputStream streamToServer = null;
		protected URLConnection server = null;
		
		public void simpleHTTP(URLConnection server) {
			this.server = server;
			server.setDoOutput(true);
			server.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
		}
		
		protected void connect() throws IOException {
			if(streamToServer == null)
				streamToServer = server.getOutputStream();
		}
		
		protected InputStream post(Map parameters) throws IOException {
			write("--");
			write(boundary);
			write("--");
			streamToServer.close();
			return server.getInputStream();
		}
		
		protected String randomString() {
			return Long.toString(random.nextLong(), 36);
		}
		
		protected void writeName(String name) throws IOException {
			writeNewLine();
		    write("Content-Disposition: form-data; name=\"");
		    write(name);
		    write('"');
		}
		
		protected void writeNewLine() throws IOException {
			connect();
			write("\r\n");
		}
		
		protected void write(char c) throws IOException {
			connect();
			streamToServer.write(c);
		}
		
		protected void write(String s) throws IOException {
			connect();
			streamToServer.write(s.getBytes());
		}
	}
	
	/**
	 * Scoreboard is a class for assembling individual scoreboards with widgets
	 * in a canvas space.
	 * 
	 * @author allometry
	 * @version 1.0
	 * @since 1.0
	 */
	public class Scoreboard {
		public static final int TOP_LEFT = 1, TOP_RIGHT = 2, BOTTOM_LEFT = 3, BOTTOM_RIGHT = 4;
		public static final int gameCanvasTop = 25, gameCanvasLeft = 25, gameCanvasBottom = 309, gameCanvasRight = 487;

		private ImageObserver observer = null;

		private int scoreboardLocation, scoreboardX, scoreboardY, scoreboardWidth,
				scoreboardHeight, scoreboardArc;

		private ArrayList<ScoreboardWidget> widgets = new ArrayList<ScoreboardWidget>();
		
		/**
		 * Creates a new instance of Scoreboard.
		 * 
		 * @param scoreboardLocation	the location of where the scoreboard should be drawn on the screen
		 * 								@see Scoreboard.TOP_LEFT
		 * 								@see Scoreboard.TOP_RIGHT
		 * 								@see Scoreboard.BOTTOM_LEFT
		 * 								@see Scoreboard.BOTTOM_RIGHT
		 * @param width					the pixel width of the scoreboard
		 * @param arc					the pixel arc of the scoreboard rounded rectangle
		 * @since 1.0
		 */
		public Scoreboard(int scoreboardLocation, int width, int arc) {
			this.scoreboardLocation = scoreboardLocation;
			scoreboardHeight = 10;
			scoreboardWidth = width;
			scoreboardArc = arc;

			switch (scoreboardLocation) {
			case 1:
				scoreboardX = gameCanvasLeft;
				scoreboardY = gameCanvasTop;
				break;

			case 2:
				scoreboardX = gameCanvasRight - scoreboardWidth;
				scoreboardY = gameCanvasTop;
				break;

			case 3:
				scoreboardX = gameCanvasLeft;
				break;

			case 4:
				scoreboardX = gameCanvasRight - scoreboardWidth;
				break;
			}
		}
		
		/**
		 * Adds a ScoreboardWidget to the Scoreboard.
		 * 
		 * @param widget				an instance of a ScoreboardWidget containing an image
		 * 								and text
		 * 								@see ScoreboardWidget
		 * @return						true if the widget was added to Scoreboard
		 * @since 1.0
		 */
		public boolean addWidget(ScoreboardWidget widget) {
			return widgets.add(widget);
		}
		
		/**
		 * Gets a ScoreboardWidget by it's index within Scoreboard.
		 * 
		 * @param widgetIndex			the index of the ScoreboardWidget
		 * @return						an instance of ScoreboardWidget
		 * @since 1.0
		 */
		public ScoreboardWidget getWidget(int widgetIndex) {
			try {
				return widgets.get(widgetIndex);
			} catch (Exception e) {
				log.warning("Warning: " + e.getMessage());
				return null;
			}
		}
		
		/**
		 * Gets the Scoreboard widgets.
		 * 
		 * @return						an ArrayList filled with ScoreboardWidget's
		 */
		public ArrayList<ScoreboardWidget> getWidgets() {
			return widgets;
		}
		
		/**
		 * Draws the Scoreboard and ScoreboardWidget's to an instances of Graphics2D.
		 * 
		 * @param g						an instance of Graphics2D
		 * @return						true if Scoreboard was able to draw to the Graphics2D instance and false if it wasn't
		 * @since 1.0
		 */
		public boolean drawScoreboard(Graphics2D g) {
			try {
				if(scoreboardHeight <= 10) {
					for (ScoreboardWidget widget : widgets) {
						scoreboardHeight += widget.getWidgetImage().getHeight(observer) + 4;
					}
				}

				if (scoreboardLocation == 3 || scoreboardLocation == 4) {
					scoreboardY = gameCanvasBottom - scoreboardHeight;
				}

				RoundRectangle2D scoreboard = new RoundRectangle2D.Float(
						scoreboardX, scoreboardY, scoreboardWidth,
						scoreboardHeight, scoreboardArc, scoreboardArc);

				g.setColor(new Color(0, 0, 0, 127));
				g.fill(scoreboard);

				int x = scoreboardX + 5;
				int y = scoreboardY + 5;
				for (ScoreboardWidget widget : widgets) {
					widget.drawWidget(g, x, y);
					y += widget.getWidgetImage().getHeight(observer) + 4;
				}

				return true;
			} catch (Exception e) {
				return false;
			}
		}
		
		/**
		 * Returns the height of the Scoreboard with respect to it's contained ScoreboardWidget's.
		 * 
		 * @return						the pixel height of the Scoreboard
		 * @since 1.0 
		 */
		public int getHeight() {
			return scoreboardHeight;
		}
	}
	
	/**
	 * ScoreboardWidget is a container intended for use with a Scoreboard. Scoreboards contain
	 * an image and text, which are later drawn to an instance of Graphics2D.
	 * 
	 * @author allometry
	 * @version 1.0
	 * @since 1.0
	 * @see Scoreboard
	 */
	public class ScoreboardWidget {
		private ImageObserver observer = null;
		private Image widgetImage;
		private String widgetText;
		
		/**
		 * Creates a new instance of ScoreboardWidget.
		 * 
		 * @param widgetImage			an instance of an Image. Recommended size is 16x16 pixels
		 * 								@see java.awt.Image
		 * @param widgetText			text to be shown on the right of the widgetImage
		 * @since 1.0
		 */
		public ScoreboardWidget(Image widgetImage, String widgetText) {
			this.widgetImage = widgetImage;
			this.widgetText = widgetText;
		}
		
		/**
		 * Gets the widget image.
		 * 
		 * @return						the Image of ScoreboardWidget
		 * 								@see java.awt.Image
		 * @since 1.0
		 */
		public Image getWidgetImage() {
			return widgetImage;
		}
		
		/**
		 * Sets the widget image.
		 * 
		 * @param widgetImage			an instance of an Image. Recommended size is 16x16 pixels
		 * 								@see java.awt.Image
		 * @since 1.0
		 */
		public void setWidgetImage(Image widgetImage) {
			this.widgetImage = widgetImage;
		}
		
		/**
		 * Gets the widget text.
		 * 
		 * @return						the text of ScoreboardWidget
		 * @since 1.0
		 */
		public String getWidgetText() {
			return widgetText;
		}
		
		/**
		 * Sets the widget text.
		 * 
		 * @param widgetText			text to be shown on the right of the widgetImage
		 * @since 1.0
		 */
		public void setWidgetText(String widgetText) {
			this.widgetText = widgetText;
		}
		
		/**
		 * Draws the ScoreboardWidget to an instance of Graphics2D.
		 * 
		 * @param g						an instance of Graphics2D
		 * @param x						horizontal pixel location of where to draw the widget 
		 * @param y						vertical pixel location of where to draw the widget
		 * @since 1.0
		 */
		public void drawWidget(Graphics2D g, int x, int y) {
			g.setColor(Color.white);
			g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));

			g.drawImage(widgetImage, x, y, observer);
			g.drawString(widgetText, x + widgetImage.getWidth(observer) + 4, y + 12);
		}
	}
}
