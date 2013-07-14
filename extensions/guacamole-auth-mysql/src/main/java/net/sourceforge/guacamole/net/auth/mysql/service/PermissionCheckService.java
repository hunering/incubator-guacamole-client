/* ***** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is guacamole-auth-mysql.
 *
 * The Initial Developer of the Original Code is
 * James Muehlner.
 * Portions created by the Initial Developer are Copyright (C) 2010
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 *
 * Alternatively, the contents of this file may be used under the terms of
 * either the GNU General Public License Version 2 or later (the "GPL"), or
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisions of the GPL or the LGPL are applicable instead
 * of those above. If you wish to allow use of your version of this file only
 * under the terms of either the GPL or the LGPL, and not to allow others to
 * use your version of this file under the terms of the MPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the MPL, the GPL or the LGPL.
 *
 * ***** END LICENSE BLOCK ***** */
package net.sourceforge.guacamole.net.auth.mysql.service;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.sourceforge.guacamole.GuacamoleSecurityException;
import net.sourceforge.guacamole.net.auth.mysql.MySQLConstants;
import net.sourceforge.guacamole.net.auth.mysql.dao.ConnectionPermissionMapper;
import net.sourceforge.guacamole.net.auth.mysql.dao.SystemPermissionMapper;
import net.sourceforge.guacamole.net.auth.mysql.dao.UserPermissionMapper;
import net.sourceforge.guacamole.net.auth.mysql.model.ConnectionPermissionExample;
import net.sourceforge.guacamole.net.auth.mysql.model.ConnectionPermissionKey;
import net.sourceforge.guacamole.net.auth.mysql.model.SystemPermissionExample;
import net.sourceforge.guacamole.net.auth.mysql.model.SystemPermissionKey;
import net.sourceforge.guacamole.net.auth.mysql.model.UserPermissionExample;
import net.sourceforge.guacamole.net.auth.mysql.model.UserPermissionKey;
import net.sourceforge.guacamole.net.auth.permission.ConnectionPermission;
import net.sourceforge.guacamole.net.auth.permission.Permission;
import net.sourceforge.guacamole.net.auth.permission.SystemPermission;
import net.sourceforge.guacamole.net.auth.permission.UserPermission;

/**
 * A service to retrieve information about what objects a user has permission to.
 * @author James Muehlner
 */
public class PermissionCheckService {

    /**
     * Service for accessing users.
     */
    @Inject
    private UserService userService;

    /**
     * Service for accessing connections.
     */
    @Inject
    private ConnectionService connectionService;

    /**
     * DAO for accessing permissions related to users.
     */
    @Inject
    private UserPermissionMapper userPermissionDAO;

    /**
     * DAO for accessing permissions related to connections.
     */
    @Inject
    private ConnectionPermissionMapper connectionPermissionDAO;

    /**
     * DAO for accessing permissions related to the system as a whole.
     */
    @Inject
    private SystemPermissionMapper systemPermissionDAO;

    /**
     * Verifies that the user has the specified access to the given other
     * user. If permission is denied, a GuacamoleSecurityException is thrown.
     *
     * @param userID The ID of the user to check.
     * @param affectedUserID The user that would be affected by the operation
     *                       if permission is granted.
     * @param permissionType The type of permission to check for.
     * @throws GuacamoleSecurityException If the specified permission is not
     *                                    granted.
     */
    public void verifyUserAccess(int userID, int affectedUserID,
            String permissionType) throws GuacamoleSecurityException {

        // If permission does not exist, throw exception
        if(!checkUserAccess(userID, affectedUserID, permissionType))
            throw new GuacamoleSecurityException("Permission denied.");

    }

    /**
     * Verifies that the user has the specified access to the given connection.
     * If permission is denied, a GuacamoleSecurityException is thrown.
     *
     * @param userID The ID of the user to check.
     * @param affectedConnectionID The connection that would be affected by the
     *                             operation if permission is granted.
     * @param permissionType The type of permission to check for.
     * @throws GuacamoleSecurityException If the specified permission is not
     *                                    granted.
     */
    public void verifyConnectionAccess(int userID, int affectedConnectionID, String permissionType) throws GuacamoleSecurityException {

        // If permission does not exist, throw exception
        if(!checkConnectionAccess(userID, affectedConnectionID, permissionType))
            throw new GuacamoleSecurityException("Permission denied.");

    }
    /**
     * Verifies that the user has the specified access to the system. If
     * permission is denied, a GuacamoleSecurityException is thrown.
     *
     * @param userID The ID of the user to check.
     * @param systemPermissionType The type of permission to check for.
     * @throws GuacamoleSecurityException If the specified permission is not
     *                                    granted.
     */
    public void verifySystemAccess(int userID, String systemPermissionType)
            throws GuacamoleSecurityException {

        // If permission does not exist, throw exception
        if(!checkSystemAccess(userID, systemPermissionType))
            throw new GuacamoleSecurityException("Permission denied.");

    }

    /**
     * Checks whether a user has the specified type of access to the affected
     * user.
     *
     * @param userID The ID of the user to check.
     * @param affectedUserID The user that would be affected by the operation
     *                       if permission is granted.
     * @param permissionType The type of permission to check for.
     * @return true if the specified permission is granted, false otherwise.
     */
    public boolean checkUserAccess(int userID, Integer affectedUserID, String permissionType) {

        // A system administrator has full access to everything.
        if(checkSystemAdministratorAccess(userID))
            return true;

        // Check existence of requested permission
        UserPermissionExample example = new UserPermissionExample();
        example.createCriteria().andUser_idEqualTo(userID).andAffected_user_idEqualTo(affectedUserID).andPermissionEqualTo(permissionType);
        return userPermissionDAO.countByExample(example) > 0;

    }

    /**
     * Checks whether a user has the specified type of access to the affected
     * connection.
     *
     * @param userID The ID of the user to check.
     * @param affectedConnectionID The connection that would be affected by the
     *                             operation if permission is granted.
     * @param permissionType The type of permission to check for.
     * @return true if the specified permission is granted, false otherwise.
     */
    public boolean checkConnectionAccess(int userID, Integer affectedConnectionID, String permissionType) {

        // A system administrator has full access to everything.
        if(checkSystemAdministratorAccess(userID))
            return true;

        // Check existence of requested permission
        ConnectionPermissionExample example = new ConnectionPermissionExample();
        example.createCriteria().andUser_idEqualTo(userID).andConnection_idEqualTo(affectedConnectionID).andPermissionEqualTo(permissionType);
        return connectionPermissionDAO.countByExample(example) > 0;

    }

    /**
     * Checks whether a user has the specified type of access to the system.
     *
     * @param userID The ID of the user to check.
     * @param systemPermissionType The type of permission to check for.
     * @return true if the specified permission is granted, false otherwise.
     */
    private boolean checkSystemAccess(int userID, String systemPermissionType) {

        // A system administrator has full access to everything.
        if(checkSystemAdministratorAccess(userID))
            return true;

        // Check existence of requested permission
        SystemPermissionExample example = new SystemPermissionExample();
        example.createCriteria().andUser_idEqualTo(userID).andPermissionEqualTo(systemPermissionType);
        return systemPermissionDAO.countByExample(example) > 0;

    }


    /**
     * Checks whether a user has system administrator access to the system.
     *
     * @param userID The ID of the user to check.
     * @return true if the system administrator access exists, false otherwise.
     */
    private boolean checkSystemAdministratorAccess(int userID) {

        // Check existence of system administrator permission
        SystemPermissionExample example = new SystemPermissionExample();
        example.createCriteria().andUser_idEqualTo(userID).
                andPermissionEqualTo(MySQLConstants.SYSTEM_ADMINISTER);
        return systemPermissionDAO.countByExample(example) > 0;
    }

    /**
     * Find the list of the IDs of all users a user has permission to.
     * The access type is defined by permissionType.
     *
     * @param userID The ID of the user to check.
     * @param permissionType The type of permission to check for.
     * @return A list of all user IDs this user has the specified access to.
     */
    public List<Integer> retrieveUserIDs(int userID, String permissionType) {

        // A system administrator has access to all users.
        if(checkSystemAdministratorAccess(userID))
            return userService.getAllUserIDs();

        // Query all user permissions for the given user and permission type
        UserPermissionExample example = new UserPermissionExample();
        example.createCriteria().andUser_idEqualTo(userID).andPermissionEqualTo(permissionType);
        example.setDistinct(true);
        List<UserPermissionKey> userPermissions =
                userPermissionDAO.selectByExample(example);

        // Convert result into list of IDs
        List<Integer> userIDs = new ArrayList<Integer>(userPermissions.size());
        for(UserPermissionKey permission : userPermissions)
            userIDs.add(permission.getAffected_user_id());

        return userIDs;

    }

    /**
     * Find the list of the IDs of all connections a user has permission to.
     * The access type is defined by permissionType.
     *
     * @param userID The ID of the user to check.
     * @param permissionType The type of permission to check for.
     * @return A list of all connection IDs this user has the specified access
     *         to.
     */
    public List<Integer> retrieveConnectionIDs(int userID,
            String permissionType) {

        // A system administrator has access to all connections.
        if(checkSystemAdministratorAccess(userID))
            return connectionService.getAllConnectionIDs();

        // Query all connection permissions for the given user and permission type
        ConnectionPermissionExample example = new ConnectionPermissionExample();
        example.createCriteria().andUser_idEqualTo(userID).andPermissionEqualTo(permissionType);
        example.setDistinct(true);
        List<ConnectionPermissionKey> connectionPermissions =
                connectionPermissionDAO.selectByExample(example);

        // Convert result into list of IDs
        List<Integer> connectionIDs = new ArrayList<Integer>(connectionPermissions.size());
        for(ConnectionPermissionKey permission : connectionPermissions)
            connectionIDs.add(permission.getConnection_id());

        return connectionIDs;

    }

    /**
     * Retrieve all existing usernames that the given user has permission to
     * perform the given operation upon.
     *
     * @param userID The user whose permissions should be checked.
     * @param permissionType The permission to check.
     * @return A set of all usernames for which the given user has the given
     *         permission.
     */
    public Set<String> retrieveUsernames(int userID, String permissionType) {

        // A system administrator has access to all users.
        if(checkSystemAdministratorAccess(userID))
            return userService.getAllUsernames();

        // List of all user IDs for which this user has read access
        List<Integer> userIDs =
                retrieveUserIDs(userID, MySQLConstants.USER_READ);

        // Query all associated users
        return userService.translateUsernames(userIDs).keySet();

    }

    /**
     * Retrieve all existing connection names that the given user has permission
     * to perform the given operation upon.
     *
     * @param userID The user whose permissions should be checked.
     * @param permissionType The permission to check.
     * @return A set of all connection names for which the given user has the
     *         given permission.
     */
    public Set<String> retrieveConnectionNames(int userID, String permissionType) {

        // A system administrator has access to all connections.
        if(checkSystemAdministratorAccess(userID))
            return connectionService.getAllConnectionNames();

        // List of all connection IDs for which this connection has read access
        List<Integer> connectionIDs =
                retrieveConnectionIDs(userID, MySQLConstants.CONNECTION_READ);

        // Query all associated connections
        return connectionService.translateNames(connectionIDs).keySet();

    }

    /**
     * Retrieves all user permissions granted to the user having the given ID.
     *
     * @param userID The ID of the user to retrieve permissions of.
     * @return A set of all user permissions granted to the user having the
     *         given ID.
     */
    public Set<UserPermission> retrieveUserPermissions(int userID) {

        // Set of all permissions
        Set<UserPermission> permissions = new HashSet<UserPermission>();

        // Query all user permissions
        UserPermissionExample userPermissionExample = new UserPermissionExample();
        userPermissionExample.createCriteria().andUser_idEqualTo(userID);
        List<UserPermissionKey> userPermissions =
                userPermissionDAO.selectByExample(userPermissionExample);

        // Get list of affected user IDs
        List<Integer> affectedUserIDs = new ArrayList<Integer>();
        for(UserPermissionKey userPermission : userPermissions)
            affectedUserIDs.add(userPermission.getAffected_user_id());

        // Get corresponding usernames
        Map<Integer, String> affectedUsers =
                userService.retrieveUsernames(affectedUserIDs);

        // Add user permissions
        for(UserPermissionKey userPermission : userPermissions) {

            // Construct permission from data
            UserPermission permission = new UserPermission(
                UserPermission.Type.valueOf(userPermission.getPermission()),
                affectedUsers.get(userPermission.getAffected_user_id())
            );

            // Add to set
            permissions.add(permission);

        }

        return permissions;

    }

    /**
     * Retrieves all connection permissions granted to the user having the
     * given ID.
     *
     * @param userID The ID of the user to retrieve permissions of.
     * @return A set of all connection permissions granted to the user having
     *         the given ID.
     */
    public Set<ConnectionPermission> retrieveConnectionPermissions(int userID) {

        // Set of all permissions
        Set<ConnectionPermission> permissions = new HashSet<ConnectionPermission>();

        // Query all connection permissions
        ConnectionPermissionExample connectionPermissionExample = new ConnectionPermissionExample();
        connectionPermissionExample.createCriteria().andUser_idEqualTo(userID);
        List<ConnectionPermissionKey> connectionPermissions =
                connectionPermissionDAO.selectByExample(connectionPermissionExample);

        // Get list of affected connection IDs
        List<Integer> connectionIDs = new ArrayList<Integer>();
        for(ConnectionPermissionKey connectionPermission : connectionPermissions)
            connectionIDs.add(connectionPermission.getConnection_id());

        // Get corresponding names
        Map<Integer, String> affectedConnections =
                connectionService.retrieveNames(connectionIDs);

        // Add connection permissions
        for(ConnectionPermissionKey connectionPermission : connectionPermissions) {

            // Construct permission from data
            ConnectionPermission permission = new ConnectionPermission(
                ConnectionPermission.Type.valueOf(connectionPermission.getPermission()),
                affectedConnections.get(connectionPermission.getConnection_id())
            );

            // Add to set
            permissions.add(permission);

        }

        return permissions;

    }

    /**
     * Retrieves all system permissions granted to the user having the
     * given ID.
     *
     * @param userID The ID of the user to retrieve permissions of.
     * @return A set of all system permissions granted to the user having the
     *         given ID.
     */
    public Set<SystemPermission> retrieveSystemPermissions(int userID) {

        // Set of all permissions
        Set<SystemPermission> permissions = new HashSet<SystemPermission>();

        // And finally, system permissions
        SystemPermissionExample systemPermissionExample = new SystemPermissionExample();
        systemPermissionExample.createCriteria().andUser_idEqualTo(userID);
        List<SystemPermissionKey> systemPermissions =
                systemPermissionDAO.selectByExample(systemPermissionExample);
        for(SystemPermissionKey systemPermission : systemPermissions) {

            // User creation permission
            if(systemPermission.getPermission().equals(MySQLConstants.SYSTEM_USER_CREATE))
                permissions.add(new SystemPermission(SystemPermission.Type.CREATE_USER));

            // System creation permission
            else if(systemPermission.getPermission().equals(MySQLConstants.SYSTEM_CONNECTION_CREATE))
                permissions.add(new SystemPermission(SystemPermission.Type.CREATE_CONNECTION));

            // System administration permission
            else if(systemPermission.getPermission().equals(MySQLConstants.SYSTEM_ADMINISTER))
                permissions.add(new SystemPermission(SystemPermission.Type.ADMINISTER));

        }

        return permissions;

    }

    /**
     * Retrieves all permissions granted to the user having the given ID.
     *
     * @param userID The ID of the user to retrieve permissions of.
     * @return A set of all permissions granted to the user having the given
     *         ID.
     */
    public Set<Permission> retrieveAllPermissions(int userID) {

        // Set which will contain all permissions
        Set<Permission> allPermissions = new HashSet<Permission>();

        // Add user permissions
        allPermissions.addAll(retrieveUserPermissions(userID));

        // Add connection permissions
        allPermissions.addAll(retrieveConnectionPermissions(userID));

        // Add system permissions
        allPermissions.addAll(retrieveSystemPermissions(userID));

        return allPermissions;
    }

}