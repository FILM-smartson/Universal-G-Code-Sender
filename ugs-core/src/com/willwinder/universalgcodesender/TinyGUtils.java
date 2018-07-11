/*
    Copyright 2013-2018 Will Winder

    This file is part of Universal Gcode Sender (UGS).

    UGS is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    UGS is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with UGS.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.willwinder.universalgcodesender;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.willwinder.universalgcodesender.gcode.GcodeState;
import com.willwinder.universalgcodesender.gcode.util.Code;
import com.willwinder.universalgcodesender.gcode.util.Plane;
import com.willwinder.universalgcodesender.listeners.ControllerState;
import com.willwinder.universalgcodesender.listeners.ControllerStatus;
import com.willwinder.universalgcodesender.model.Axis;
import com.willwinder.universalgcodesender.model.Position;
import com.willwinder.universalgcodesender.model.UnitUtils;
import org.apache.commons.lang3.StringUtils;

import static com.willwinder.universalgcodesender.gcode.util.Code.G20;

/**
 * Common utils for TinyG controllers
 *
 * @author wwinder
 * @author Joacim Breiler
 */
public class TinyGUtils {

    public static final byte COMMAND_PAUSE = '!';
    public static final byte COMMAND_RESUME = '~';
    public static final byte COMMAND_STATUS = '?';
    public static final byte COMMAND_QUEUE_FLUSH = '%';
    public static final byte COMMAND_KILL_JOB = 0x04;
    public static final byte COMMAND_ENQUIRE_STATUS = 0x05;
    public static final byte COMMAND_RESET = 0x18;

    public static final String COMMAND_STATUS_REPORT = "{sr:n}";
    public static final String COMMAND_KILL_ALARM_LOCK = "{clear:n}";

    private static final String FIELD_FIRMWARE_VERSION = "fv";
    private static final String FIELD_RESPONSE = "r";

    public static final String FIELD_STATUS_RESULT = "sr";
    private static final String FIELD_STATUS_RESULT_UNIT = "unit";
    private static final String FIELD_STATUS_RESULT_POSX = "posx";
    private static final String FIELD_STATUS_REPORT_POSY = "posy";
    private static final String FIELD_STATUS_REPORT_POSZ = "posz";
    private static final String FIELD_STATUS_REPORT_VELOCITY = "vel";
    private static final String FIELD_STATUS_REPORT_COORD = "coor";
    private static final String FIELD_STATUS_REPORT_PLANE = "plan";
    private static final String FIELD_STATUS_REPORT_DISTANCE_MODE = "dist";
    private static final String FIELD_STATUS_REPORT_ARC_DISTANCE_MODE = "admo";
    private static final String FIELD_STATUS_REPORT_FEED_MODE = "frmo";
    private static final String FIELD_STATUS_REPORT_STATUS = "stat";
    private static final String FIELD_STATUS_REPORT_MPOX = "mpox";
    private static final String FIELD_STATUS_REPORT_MPOY = "mpoy";
    private static final String FIELD_STATUS_REPORT_MPOZ = "mpoz";

    private static JsonParser parser = new JsonParser();

    public static JsonObject jsonToObject(String response) {
        return parser.parse(response).getAsJsonObject();
    }

    public static boolean isTinyGVersion(JsonObject response) {
        if (response.has(FIELD_RESPONSE)) {
            JsonObject jo = response.getAsJsonObject(FIELD_RESPONSE);
            if (jo.has(FIELD_FIRMWARE_VERSION)) {
                return true;
            }
        }
        return false;
    }

    public static String getVersion(JsonObject response) {
        if (response.has(FIELD_RESPONSE)) {
            JsonObject jo = response.getAsJsonObject(FIELD_RESPONSE);
            if (jo.has(FIELD_FIRMWARE_VERSION)) {
                return jo.get(FIELD_FIRMWARE_VERSION).getAsString();
            }
        }
        return "";
    }

    public static boolean isRestartingResponse(JsonObject response) {
        if (response.has(FIELD_RESPONSE)) {
            JsonObject jo = response.getAsJsonObject(FIELD_RESPONSE);
            if (jo.has("msg")) {
                String msg = jo.get("msg").getAsString();
                return StringUtils.equals(msg, "Loading configs from EEPROM");
            }
        }
        return false;
    }

    public static boolean isReadyResponse(JsonObject response) {
        if (response.has(FIELD_RESPONSE)) {
            JsonObject jo = response.getAsJsonObject(FIELD_RESPONSE);
            if (jo.has("msg")) {
                String msg = jo.get("msg").getAsString();
                return StringUtils.equals(msg, "SYSTEM READY");
            }
        }
        return false;
    }

    public static boolean isStatusResponse(JsonObject response) {
        return response.has("sr");
    }

    /**
     * Parses the TinyG status result response and creates a new current controller status
     *
     * @param lastControllerStatus the last controller status to update
     * @param response             the response string from the controller
     * @return a new updated controller status
     */
    public static ControllerStatus updateControllerStatus(final ControllerStatus lastControllerStatus, final GcodeState lastGcodeState, final JsonObject response) {
        if (response.has(FIELD_STATUS_RESULT)) {
            JsonObject statusResultObject = response.getAsJsonObject(FIELD_STATUS_RESULT);

            UnitUtils.Units currentUnits = lastGcodeState.units == G20 ? UnitUtils.Units.INCH : UnitUtils.Units.MM;

            Position workCoord = lastControllerStatus.getWorkCoord().getPositionIn(currentUnits);
            if (statusResultObject.has(FIELD_STATUS_RESULT_POSX)) {
                workCoord.setX(statusResultObject.get(FIELD_STATUS_RESULT_POSX).getAsDouble());
            }

            if (statusResultObject.has(FIELD_STATUS_REPORT_POSY)) {
                workCoord.setY(statusResultObject.get(FIELD_STATUS_REPORT_POSY).getAsDouble());
            }

            if (statusResultObject.has(FIELD_STATUS_REPORT_POSZ)) {
                workCoord.setZ(statusResultObject.get(FIELD_STATUS_REPORT_POSZ).getAsDouble());
            }

            Position machineCoord = lastControllerStatus.getMachineCoord().getPositionIn(UnitUtils.Units.MM);
            if (statusResultObject.has(FIELD_STATUS_REPORT_MPOX)) {
                machineCoord.setX(statusResultObject.get(FIELD_STATUS_REPORT_MPOX).getAsDouble());
            }

            if (statusResultObject.has(FIELD_STATUS_REPORT_MPOY)) {
                machineCoord.setY(statusResultObject.get(FIELD_STATUS_REPORT_MPOY).getAsDouble());
            }

            if (statusResultObject.has(FIELD_STATUS_REPORT_MPOZ)) {
                machineCoord.setZ(statusResultObject.get(FIELD_STATUS_REPORT_MPOZ).getAsDouble());
            }

            Double feedSpeed = lastControllerStatus.getFeedSpeed();
            if (statusResultObject.has(FIELD_STATUS_REPORT_VELOCITY)) {
                feedSpeed = statusResultObject.get(FIELD_STATUS_REPORT_VELOCITY).getAsDouble();
            }

            ControllerState state = lastControllerStatus.getState();
            String stateString = lastControllerStatus.getStateString();
            if (statusResultObject.has(FIELD_STATUS_REPORT_STATUS)) {
                state = getState(statusResultObject.get(FIELD_STATUS_REPORT_STATUS).getAsInt());
                stateString = getStateAsString(statusResultObject.get(FIELD_STATUS_REPORT_STATUS).getAsInt());
            }

            Double spindleSpeed = lastControllerStatus.getSpindleSpeed();
            ControllerStatus.OverridePercents overrides = lastControllerStatus.getOverrides();
            Position workCoordinateOffset = lastControllerStatus.getWorkCoordinateOffset();
            ControllerStatus.EnabledPins enabledPins = lastControllerStatus.getEnabledPins();
            ControllerStatus.AccessoryStates accessoryStates = lastControllerStatus.getAccessoryStates();

            return new ControllerStatus(stateString, state, machineCoord.getPositionIn(currentUnits), workCoord.getPositionIn(currentUnits), feedSpeed, spindleSpeed, overrides, workCoordinateOffset, enabledPins, accessoryStates);
        }

        return lastControllerStatus;
    }

    /**
     * Maps between the TinyG state to a ControllerState
     *
     * @param state the state flag from a TinyGController
     * @return a corresponding ControllerState
     */
    private static ControllerState getState(int state) {
        switch (state) {
            case 0: // Machine is initializing
                return ControllerState.UNKNOWN;
            case 1: // Machine is ready for use
                return ControllerState.IDLE;
            case 2: // Machine is in alarm state
                return ControllerState.ALARM;
            case 3: // Machine has encountered program stop
                return ControllerState.IDLE;
            case 4: // Machine has encountered program end
                return ControllerState.IDLE;
            case 5: // Machine is running
                return ControllerState.RUN;
            case 6: // Machine is holding
                return ControllerState.HOLD;
            case 7: // Machine is in probing operation
                return ControllerState.UNKNOWN;
            case 8: // Reserved for canned cycles (not used)
                return ControllerState.UNKNOWN;
            case 9: // Machine is in a homing cycle
                return ControllerState.HOME;
            case 10: // Machine is in a jogging cycle
                return ControllerState.JOG;
            case 11: // Machine is in safety interlock hold
                return ControllerState.UNKNOWN;
            case 12: // Machine is in shutdown state. Will not process commands
                return ControllerState.UNKNOWN;
            case 13: // Machine is in panic state. Needs to be physically reset
                return ControllerState.ALARM;
            default:
                return ControllerState.UNKNOWN;
        }
    }

    private static String getStateAsString(int state) {
        ControllerState controllerState = getState(state);
        return controllerState.name();
    }

    /**
     * Generates a command for resetting the coordinates for the current coordinate system to zero.
     *
     * @param controllerStatus the current controller status
     * @param gcodeState       the current gcode state
     * @return a string with the command to reset the coordinate system to zero
     */
    public static String generateResetCoordinatesToZeroCommand(ControllerStatus controllerStatus, GcodeState gcodeState) {
        int offsetCode = convertOffsetGcodeToCode(gcodeState.offset);
        Position machineCoord = controllerStatus.getMachineCoord();
        return "G10 L2 P" + offsetCode +
                " X" + Utils.formatter.format(machineCoord.get(Axis.X)) +
                " Y" + Utils.formatter.format(machineCoord.get(Axis.Y)) +
                " Z" + Utils.formatter.format(machineCoord.get(Axis.Z));
    }

    /**
     * Generates a command for setting the axis to a position in the current coordinate system
     *
     * @param controllerStatus the current controller status
     * @param gcodeState       the current gcode state
     * @param axis             the axis to set
     * @param position         the position to set
     * @return a command for setting the position
     */
    public static String generateSetWorkPositionCommand(ControllerStatus controllerStatus, GcodeState gcodeState, Axis axis, double position) {
        int offsetCode = convertOffsetGcodeToCode(gcodeState.offset);
        Position machineCoord = controllerStatus.getMachineCoord();
        double coordinate = -(position - machineCoord.get(axis));
        return "G10 L2 P" + offsetCode + " " +
                axis.name() + Utils.formatter.format(coordinate);
    }

    private static int convertOffsetGcodeToCode(Code offsetGcode) {
        switch (offsetGcode) {
            case G54:
                return 1;
            case G55:
                return 2;
            case G56:
                return 3;
            case G57:
                return 4;
            case G58:
                return 5;
            case G59:
                return 6;
            default:
                return 0;
        }
    }

    private static Code convertOffsetCodeToGcode(int offsetCode) {
        switch (offsetCode) {
            case 1:
                return Code.G54;
            case 2:
                return Code.G55;
            case 3:
                return Code.G56;
            case 4:
                return Code.G57;
            case 5:
                return Code.G58;
            case 6:
                return Code.G59;
            default:
                return Code.G53;
        }
    }

    /**
     * Updates the Gcode state from the response if it contains a status line
     *
     * @param controller the controller to update
     * @param response   the response to parse the gcode state from
     */
    public static void updateGcodeState(TinyGController controller, JsonObject response) {
        if (response.has(TinyGUtils.FIELD_STATUS_RESULT)) {
            GcodeState gcodeState = controller.getCurrentGcodeState();
            JsonObject statusResultObject = response.getAsJsonObject(TinyGUtils.FIELD_STATUS_RESULT);

            if (statusResultObject.has(TinyGUtils.FIELD_STATUS_REPORT_COORD)) {
                int offsetCode = statusResultObject.get(TinyGUtils.FIELD_STATUS_REPORT_COORD).getAsInt();
                gcodeState.offset = TinyGUtils.convertOffsetCodeToGcode(offsetCode);
            }

            if (statusResultObject.has(TinyGUtils.FIELD_STATUS_RESULT_UNIT)) {
                int units = statusResultObject.get(TinyGUtils.FIELD_STATUS_RESULT_UNIT).getAsInt();
                // 0=inch, 1=mm
                if (units == 0) {
                    gcodeState.units = Code.G20;
                    controller.setUnitsCode(Code.G20.name());
                } else {
                    gcodeState.units = Code.G21;
                    controller.setUnitsCode(Code.G21.name());
                }
            }

            if (statusResultObject.has(TinyGUtils.FIELD_STATUS_REPORT_PLANE)) {
                int plane = statusResultObject.get(TinyGUtils.FIELD_STATUS_REPORT_PLANE).getAsInt();
                // 0=XY plane, 1=XZ plane, 2=YZ plane
                if (plane == 0) {
                    gcodeState.plane = Plane.XY;
                } else if (plane == 1) {
                    gcodeState.plane = Plane.ZX;
                } else if (plane == 2) {
                    gcodeState.plane = Plane.YZ;
                }
            }

            if (statusResultObject.has(TinyGUtils.FIELD_STATUS_REPORT_FEED_MODE)) {
                int feedMode = statusResultObject.get(TinyGUtils.FIELD_STATUS_REPORT_FEED_MODE).getAsInt();
                // 0=units-per-minute-mode, 1=inverse-time-mode
                if (feedMode == 0) {
                    gcodeState.feedMode = Code.G93;
                } else if (feedMode == 1) {
                    gcodeState.feedMode = Code.G94;
                }
            }

            if (statusResultObject.has(TinyGUtils.FIELD_STATUS_REPORT_DISTANCE_MODE)) {
                int distance = statusResultObject.get(TinyGUtils.FIELD_STATUS_REPORT_DISTANCE_MODE).getAsInt();
                // 0=absolute distance mode, 1=incremental distance mode
                if (distance == 0) {
                    gcodeState.distanceMode = Code.G90;
                    controller.setDistanceModeCode(Code.G90.name());
                } else if (distance == 1) {
                    gcodeState.distanceMode = Code.G91;
                    controller.setDistanceModeCode(Code.G91.name());
                }
            }

            if (statusResultObject.has(TinyGUtils.FIELD_STATUS_REPORT_ARC_DISTANCE_MODE)) {
                int arcDistance = statusResultObject.get(TinyGUtils.FIELD_STATUS_REPORT_ARC_DISTANCE_MODE).getAsInt();
                // 0=absolute distance mode, 1=incremental distance mode
                if (arcDistance == 0) {
                    gcodeState.arcDistanceMode = Code.G90_1;
                } else if (arcDistance == 1) {
                    gcodeState.arcDistanceMode = Code.G91_1;
                }
            }
        }

    }
}
