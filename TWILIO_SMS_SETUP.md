# Twilio SMS Integration for WYPE Security App

## Overview
The WYPE security app now supports Twilio SMS for silent emergency message sending. Twilio provides more reliable SMS delivery compared to local carrier SMS, especially for international numbers or when the device has poor cellular reception.

## Features
- **Silent SMS sending**: Twilio SMS works in the background without user interaction
- **Automatic fallback**: If Twilio fails, the app automatically falls back to local SMS
- **International support**: Twilio supports SMS to virtually any country
- **Reliable delivery**: Better delivery rates than carrier SMS
- **Status tracking**: Full logging of SMS send attempts and results

## How It Works
1. When an emergency phrase is detected, the app first tries to send SMS via Twilio
2. If Twilio is not configured or fails, it automatically falls back to local Android SMS
3. All SMS attempts are logged for debugging and verification

## Setup Instructions

### 1. Get Twilio Credentials
1. Sign up for a Twilio account at https://www.twilio.com
2. Go to the Twilio Console
3. Note down your **Account SID** and **Auth Token**
4. Purchase a Twilio phone number for sending SMS

### 2. Configure in App
1. Open the WYPE app and go to the **Buddy** tab
2. **Long press** the "Test SMS" button
3. Enter your Twilio credentials:
   - **Account SID**: Your Twilio account identifier
   - **Auth Token**: Your Twilio authentication token  
   - **From Phone**: Your Twilio phone number (format: +1234567890)
4. Check "Enable Twilio SMS" to activate it
5. Tap "Save"

### 3. Testing
- Use the regular "Test SMS" button to test SMS sending
- The app will use Twilio if configured, otherwise local SMS
- Check the app logs to see which method was used

## Phone Number Format
- All phone numbers must be in international format (e.g., +1234567890)
- The app automatically formats numbers if needed
- Buddy phone numbers are validated for international format

## Fallback Behavior
If Twilio SMS fails for any reason:
- Network connectivity issues
- Invalid credentials  
- Twilio service outage
- Account issues (insufficient funds, etc.)

The app will automatically fall back to local Android SMS using the device's carrier connection.

## Security Considerations
- Twilio credentials are stored in Android SharedPreferences
- Auth tokens are stored as password fields (masked input)
- All SMS sending happens in background without UI notifications
- Failed SMS attempts are logged for debugging

## Troubleshooting

### Common Issues
1. **SMS not sending via Twilio**
   - Check internet connectivity
   - Verify Twilio credentials are correct
   - Ensure Twilio account has sufficient balance
   - Check if buddy phone number is in international format

2. **Fallback to local SMS**
   - This is normal if Twilio is not configured or fails
   - Check app logs to see the reason for fallback

3. **No SMS at all**
   - Ensure SMS permissions are granted
   - Check if buddy contact is configured
   - Verify emergency phrase detection is working

### Logs to Check
Look for these log tags in Android Studio or device logs:
- `TwilioSmsService`: Twilio SMS operations
- `EmergencySmsService`: Overall SMS sending logic
- `SpeechListenerService`: Emergency phrase detection

## Cost Considerations
- Twilio charges per SMS sent (typically $0.0075 per SMS in US)
- Emergency SMS will only be sent when the wake phrase is detected
- Consider this cost when deciding whether to enable Twilio

## Disabling Twilio
To disable Twilio and use only local SMS:
1. Long press the "Test SMS" button in Buddy tab
2. Uncheck "Enable Twilio SMS"
3. Tap "Save"

Or use the "Clear" button to remove all Twilio credentials entirely.
