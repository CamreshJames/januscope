package ke.skyworld.januscope.api.handlers;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.StatusCodes;
import ke.skyworld.januscope.domain.repositories.ContactGroupRepository;
import ke.skyworld.januscope.domain.repositories.ContactMemberRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Contact Group Management API Handler
 * Endpoints: /api/contact-groups/*
 */
public class ContactGroupHandler extends BaseHandler {
    private final ContactGroupRepository groupRepository;
    private final ContactMemberRepository memberRepository;
    
    public ContactGroupHandler(ContactGroupRepository groupRepository,
                              ContactMemberRepository memberRepository) {
        this.groupRepository = groupRepository;
        this.memberRepository = memberRepository;
    }
    
    @Override
    protected void handleGet(HttpServerExchange exchange) throws Exception {
        String path = exchange.getRequestPath();
        
        if (path.matches(".*/contact-groups/\\d+/members$")) {
            handleGetGroupMembers(exchange);
        } else if (path.matches(".*/contact-groups/\\d+$")) {
            handleGetGroup(exchange);
        } else {
            handleGetAllGroups(exchange);
        }
    }
    
    @Override
    protected void handlePost(HttpServerExchange exchange) throws Exception {
        String path = exchange.getRequestPath();
        
        if (path.matches(".*/contact-groups/\\d+/members$")) {
            handleAddMember(exchange);
        } else {
            handleCreateGroup(exchange);
        }
    }
    
    @Override
    protected void handlePut(HttpServerExchange exchange) throws Exception {
        String path = exchange.getRequestPath();
        
        if (path.matches(".*/contact-groups/\\d+/members/\\d+$")) {
            handleUpdateMember(exchange);
        } else if (path.matches(".*/contact-groups/\\d+$")) {
            handleUpdateGroup(exchange);
        } else {
            sendError(exchange, StatusCodes.BAD_REQUEST, "Invalid endpoint");
        }
    }
    
    @Override
    protected void handleDelete(HttpServerExchange exchange) throws Exception {
        String path = exchange.getRequestPath();
        
        if (path.matches(".*/contact-groups/\\d+/members/\\d+$")) {
            handleDeleteMember(exchange);
        } else if (path.matches(".*/contact-groups/\\d+$")) {
            handleDeleteGroup(exchange);
        } else {
            sendError(exchange, StatusCodes.BAD_REQUEST, "Invalid endpoint");
        }
    }
    
    // GET /api/contact-groups
    private void handleGetAllGroups(HttpServerExchange exchange) {
        try {
            List<Map<String, Object>> groups = groupRepository.findAll();
            sendSuccess(exchange, groups);
        } catch (Exception e) {
            logger.error("Failed to get contact groups", e);
            sendError(exchange, StatusCodes.INTERNAL_SERVER_ERROR, "Failed to retrieve contact groups");
        }
    }
    
    // GET /api/contact-groups/{id}
    private void handleGetGroup(HttpServerExchange exchange) {
        try {
            int groupId = extractIdFromPath(exchange.getRequestPath());
            Map<String, Object> group = groupRepository.findById(groupId);
            
            if (group == null) {
                sendError(exchange, StatusCodes.NOT_FOUND, "Contact group not found");
                return;
            }
            
            sendSuccess(exchange, group);
        } catch (Exception e) {
            logger.error("Failed to get contact group", e);
            sendError(exchange, StatusCodes.INTERNAL_SERVER_ERROR, "Failed to retrieve contact group");
        }
    }
    
    // GET /api/contact-groups/{id}/members
    private void handleGetGroupMembers(HttpServerExchange exchange) {
        try {
            int groupId = extractIdFromPath(exchange.getRequestPath());
            List<Map<String, Object>> members = memberRepository.findByGroupId(groupId);
            sendSuccess(exchange, members);
        } catch (Exception e) {
            logger.error("Failed to get group members", e);
            sendError(exchange, StatusCodes.INTERNAL_SERVER_ERROR, "Failed to retrieve group members");
        }
    }
    
    // POST /api/contact-groups
    private void handleCreateGroup(HttpServerExchange exchange) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) parseRequestBody(exchange, Map.class);
            
            // Validate required fields
            if (!data.containsKey("name")) {
                sendError(exchange, StatusCodes.BAD_REQUEST, "Group name is required");
                return;
            }
            
            int groupId = groupRepository.create(data);
            
            Map<String, Object> response = new HashMap<>();
            response.put("groupId", groupId);
            response.put("message", "Contact group created successfully");
            
            sendSuccess(exchange, response);
        } catch (Exception e) {
            logger.error("Failed to create contact group", e);
            sendError(exchange, StatusCodes.INTERNAL_SERVER_ERROR, "Failed to create contact group");
        }
    }
    
    // POST /api/contact-groups/{id}/members
    private void handleAddMember(HttpServerExchange exchange) {
        try {
            int groupId = extractIdFromPath(exchange.getRequestPath());
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) parseRequestBody(exchange, Map.class);
            
            // Validate required fields
            if (!data.containsKey("name")) {
                sendError(exchange, StatusCodes.BAD_REQUEST, "Member name is required");
                return;
            }
            
            // At least one contact method required
            if (!data.containsKey("email") && !data.containsKey("telegramHandle") && !data.containsKey("phoneNumber")) {
                sendError(exchange, StatusCodes.BAD_REQUEST, "At least one contact method (email, telegram, or phone) is required");
                return;
            }
            
            data.put("groupId", groupId);
            int memberId = memberRepository.create(data);
            
            Map<String, Object> response = new HashMap<>();
            response.put("memberId", memberId);
            response.put("message", "Member added successfully");
            
            sendSuccess(exchange, response);
        } catch (Exception e) {
            logger.error("Failed to add member", e);
            sendError(exchange, StatusCodes.INTERNAL_SERVER_ERROR, "Failed to add member");
        }
    }
    
    // PUT /api/contact-groups/{id}
    private void handleUpdateGroup(HttpServerExchange exchange) {
        try {
            int groupId = extractIdFromPath(exchange.getRequestPath());
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) parseRequestBody(exchange, Map.class);
            
            boolean updated = groupRepository.update(groupId, data);
            
            if (!updated) {
                sendError(exchange, StatusCodes.NOT_FOUND, "Contact group not found");
                return;
            }
            
            sendSuccess(exchange, Map.of("message", "Contact group updated successfully"));
        } catch (Exception e) {
            logger.error("Failed to update contact group", e);
            sendError(exchange, StatusCodes.INTERNAL_SERVER_ERROR, "Failed to update contact group");
        }
    }
    
    // PUT /api/contact-groups/{groupId}/members/{memberId}
    private void handleUpdateMember(HttpServerExchange exchange) {
        try {
            String path = exchange.getRequestPath();
            String[] parts = path.split("/");
            int memberId = Integer.parseInt(parts[parts.length - 1]);
            
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) parseRequestBody(exchange, Map.class);
            boolean updated = memberRepository.update(memberId, data);
            
            if (!updated) {
                sendError(exchange, StatusCodes.NOT_FOUND, "Member not found");
                return;
            }
            
            sendSuccess(exchange, Map.of("message", "Member updated successfully"));
        } catch (Exception e) {
            logger.error("Failed to update member", e);
            sendError(exchange, StatusCodes.INTERNAL_SERVER_ERROR, "Failed to update member");
        }
    }
    
    // DELETE /api/contact-groups/{id}
    private void handleDeleteGroup(HttpServerExchange exchange) {
        try {
            int groupId = extractIdFromPath(exchange.getRequestPath());
            boolean deleted = groupRepository.delete(groupId);
            
            if (!deleted) {
                sendError(exchange, StatusCodes.NOT_FOUND, "Contact group not found");
                return;
            }
            
            sendSuccess(exchange, Map.of("message", "Contact group deleted successfully"));
        } catch (Exception e) {
            logger.error("Failed to delete contact group", e);
            sendError(exchange, StatusCodes.INTERNAL_SERVER_ERROR, "Failed to delete contact group");
        }
    }
    
    // DELETE /api/contact-groups/{groupId}/members/{memberId}
    private void handleDeleteMember(HttpServerExchange exchange) {
        try {
            String path = exchange.getRequestPath();
            String[] parts = path.split("/");
            int memberId = Integer.parseInt(parts[parts.length - 1]);
            
            boolean deleted = memberRepository.delete(memberId);
            
            if (!deleted) {
                sendError(exchange, StatusCodes.NOT_FOUND, "Member not found");
                return;
            }
            
            sendSuccess(exchange, Map.of("message", "Member deleted successfully"));
        } catch (Exception e) {
            logger.error("Failed to delete member", e);
            sendError(exchange, StatusCodes.INTERNAL_SERVER_ERROR, "Failed to delete member");
        }
    }
    
    /**
     * Extract ID from path like /api/contact-groups/123
     */
    private int extractIdFromPath(String path) {
        String[] parts = path.split("/");
        for (int i = parts.length - 1; i >= 0; i--) {
            if (parts[i].matches("\\d+")) {
                return Integer.parseInt(parts[i]);
            }
        }
        throw new IllegalArgumentException("No ID found in path: " + path);
    }
}
