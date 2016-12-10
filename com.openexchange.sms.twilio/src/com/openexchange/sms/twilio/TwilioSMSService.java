/*
 *
 *    OPEN-XCHANGE legal information
 *
 *    All intellectual property rights in the Software are protected by
 *    international copyright laws.
 *
 *
 *    In some countries OX, OX Open-Xchange, open xchange and OXtender
 *    as well as the corresponding Logos OX Open-Xchange and OX are registered
 *    trademarks of the OX Software GmbH group of companies.
 *    The use of the Logos is not covered by the GNU General Public License.
 *    Instead, you are allowed to use these Logos according to the terms and
 *    conditions of the Creative Commons License, Version 2.5, Attribution,
 *    Non-commercial, ShareAlike, and the interpretation of the term
 *    Non-commercial applicable to the aforementioned license is published
 *    on the web site http://www.open-xchange.com/EN/legal/index.html.
 *
 *    Please make sure that third-party modules and libraries are used
 *    according to their respective licenses.
 *
 *    Any modifications to this package must retain all copyright notices
 *    of the original copyright holder(s) for the original code used.
 *
 *    After any such modifications, the original and derivative code shall remain
 *    under the copyright of the copyright holder(s) and/or original author(s)per
 *    the Attribution and Assignment Agreement that can be located at
 *    http://www.open-xchange.com/EN/developer/. The contributing author shall be
 *    given Attribution for the derivative code and a license granting use.
 *
 *     Copyright (C) 2016-2020 OX Software GmbH
 *     Mail: info@open-xchange.com
 *
 *
 *     This program is free software; you can redistribute it and/or modify it
 *     under the terms of the GNU General Public License, Version 2 as published
 *     by the Free Software Foundation.
 *
 *     This program is distributed in the hope that it will be useful, but
 *     WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 *     or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 *     for more details.
 *
 *     You should have received a copy of the GNU General Public License along
 *     with this program; if not, write to the Free Software Foundation, Inc., 59
 *     Temple Place, Suite 330, Boston, MA 02111-1307 USA
 *
 */

package com.openexchange.sms.twilio;

import java.util.Locale;
import com.openexchange.config.ConfigurationService;
import com.openexchange.exception.OXException;
import com.openexchange.server.ServiceLookup;
import com.openexchange.sms.PhoneNumberParserService;
import com.openexchange.sms.SMSExceptionCode;
import com.openexchange.sms.SMSServiceSPI;
import com.openexchange.sms.twilio.osgi.TwilioSMSActivator;
import com.twilio.Twilio;
import com.twilio.exception.ApiException;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.rest.api.v2010.account.MessageCreator;
import com.twilio.type.PhoneNumber;

/**
 * {@link TwilioSMSService}
 *
 * @author <a href="mailto:hv@webhuset.no">Hogne Vevle</a>
 * @since v7.8.3
 */
public class TwilioSMSService implements SMSServiceSPI {
	
    private org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(TwilioSMSService.class);
    private final ServiceLookup services;
    
    private final int MAX_MESSAGE_LENGTH;
    private final String MESSAGE_SERVICE_SID;
    private final String AUTH_KEY;
    private final String AUTH_TOKEN;

    /**
     * Initializes a new {@link TwilioSMSService}.
     *
     * @throws OXException
     */
    public TwilioSMSService(ServiceLookup services) {
        this.services = services;
        
        logger.debug("Initializing Twilio SMS service...");
                       
        ConfigurationService configService = services.getService(ConfigurationService.class);
        MAX_MESSAGE_LENGTH = configService.getIntProperty("com.openexchange.sms.twilio.maxlength", 1600);
        MESSAGE_SERVICE_SID = configService.getProperty("com.openexchange.sms.twilio.messageservicesid");

        AUTH_KEY = configService.getProperty("com.openexchange.sms.twilio.authkey");
        AUTH_TOKEN = configService.getProperty("com.openexchange.sms.twilio.authtoken");
        
        if (MESSAGE_SERVICE_SID == null) {
        	logger.warn("Property not defined: com.openexchange.sms.twilio.messageservicesid");
        }
        
        if (AUTH_KEY == null) {
        	logger.warn("Property not defined: com.openexchange.sms.twilio.authkey");
        }
        
        if (AUTH_TOKEN == null) {
        	logger.warn("Property not defined: com.openexchange.sms.twilio.authtoken");
        }
    }

    @Override
    public void sendMessage(String[] recipients, String message) throws OXException {
        if (MAX_MESSAGE_LENGTH > 0 && message.length() > MAX_MESSAGE_LENGTH) {
            throw SMSExceptionCode.MESSAGE_TOO_LONG.create(message.length(), MAX_MESSAGE_LENGTH);
        }
        
        Twilio.init(AUTH_KEY, AUTH_TOKEN);
        
        for (int i = 0; i < recipients.length; i++) {
	        try {        
	        	String recipient = checkAndFormatPhoneNumber(recipients[i], null);
	        	logger.debug(String.format("To: %1$s, Body: %2$s", recipient, message));
	        	
	        	MessageCreator msgCreator = Message.creator(
	        			new PhoneNumber(recipient), 
	        			MESSAGE_SERVICE_SID, 
	        			message);
	        	
	        	Message res = msgCreator.create();
	        	logger.info("SMS submitted to Twilio. SID: " + res.getSid());
	            
	        } catch (ApiException e) {
	        	logger.error(String.format("Failed to send SMS. Error code: %1$s. Error message: %2$s.", 
	        			e.getStatusCode(), e.getMessage()));
	        	throw TwilioSMSExceptionCode.UNKNOWN_ERROR.create(e, e.getMessage());
	    	} catch (Exception e) {
	    		logger.error("Unknown error occured during sending of SMS: " + e.getMessage());
	            throw TwilioSMSExceptionCode.UNKNOWN_ERROR.create(e, e.getMessage());
	        } 
        }
    }

    private String checkAndFormatPhoneNumber(String phoneNumber, Locale locale) throws OXException {
    	PhoneNumberParserService parser = services.getService(PhoneNumberParserService.class);
        
        // As parsePhoneNumber() removes the '+' which we need, we only use it to verify the number's validity. 
        parser.parsePhoneNumber(phoneNumber, locale);
        
        // Remove any whitespace characters
        return phoneNumber.replaceAll("\\s+","");
    }
}
