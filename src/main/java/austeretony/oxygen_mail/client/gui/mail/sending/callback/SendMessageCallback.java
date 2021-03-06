package austeretony.oxygen_mail.client.gui.mail.sending.callback;

import javax.annotation.Nullable;

import org.lwjgl.input.Keyboard;

import austeretony.alternateui.screen.callback.AbstractGUICallback;
import austeretony.alternateui.screen.core.AbstractGUISection;
import austeretony.alternateui.screen.core.GUIBaseElement;
import austeretony.oxygen_core.client.api.ClientReference;
import austeretony.oxygen_core.client.api.EnumBaseGUISetting;
import austeretony.oxygen_core.client.api.PrivilegesProviderClient;
import austeretony.oxygen_core.client.gui.elements.OxygenCallbackBackgroundFiller;
import austeretony.oxygen_core.client.gui.elements.OxygenCurrencyValue;
import austeretony.oxygen_core.client.gui.elements.OxygenKeyButton;
import austeretony.oxygen_core.client.gui.elements.OxygenTextLabel;
import austeretony.oxygen_core.common.main.OxygenMain;
import austeretony.oxygen_core.common.util.MathUtils;
import austeretony.oxygen_mail.client.MailManagerClient;
import austeretony.oxygen_mail.client.gui.mail.MailMenuScreen;
import austeretony.oxygen_mail.client.gui.mail.MessageAttachment;
import austeretony.oxygen_mail.client.gui.mail.SendingSection;
import austeretony.oxygen_mail.common.config.MailConfig;
import austeretony.oxygen_mail.common.mail.Attachment;
import austeretony.oxygen_mail.common.mail.Attachments;
import austeretony.oxygen_mail.common.mail.EnumMail;
import austeretony.oxygen_mail.common.main.EnumMailPrivilege;

public class SendMessageCallback extends AbstractGUICallback {

    private final MailMenuScreen screen;

    private final SendingSection section;

    private OxygenKeyButton confirmButton, cancelButton;

    private OxygenTextLabel messageTypeTextLabel, attachmentNoticeTextLabel, addresseeTextLabel, postageTextLabel;

    private MessageAttachment attachmentWidget;

    private OxygenCurrencyValue postageValue;

    //cache

    private EnumMail type;

    private String subject, message;

    @Nullable
    private Attachment attachment;

    public SendMessageCallback(MailMenuScreen screen, SendingSection section, int width, int height) {
        super(screen, section, width, height);
        this.screen = screen;
        this.section = section;
    }

    @Override
    public void init() {
        this.enableDefaultBackground(EnumBaseGUISetting.FILL_CALLBACK_COLOR.get().asInt());
        this.addElement(new OxygenCallbackBackgroundFiller(0, 0, this.getWidth(), this.getHeight()));
        this.addElement(new OxygenTextLabel(4, 12, ClientReference.localize("oxygen_mail.gui.mail.callback.sendMessage"), EnumBaseGUISetting.TEXT_SCALE.get().asFloat(), EnumBaseGUISetting.TEXT_ENABLED_COLOR.get().asInt()));

        this.addElement(this.messageTypeTextLabel = new OxygenTextLabel(6, 23, "", EnumBaseGUISetting.TEXT_SCALE.get().asFloat(), EnumBaseGUISetting.TEXT_ENABLED_COLOR.get().asInt()));
        this.addElement(this.addresseeTextLabel = new OxygenTextLabel(6, 32, "", EnumBaseGUISetting.TEXT_SUB_SCALE.get().asFloat(), EnumBaseGUISetting.TEXT_DARK_ENABLED_COLOR.get().asInt()));
        this.addElement(this.attachmentNoticeTextLabel = new OxygenTextLabel(6, 41, ClientReference.localize("oxygen_mail.gui.mail.noAttachment"), EnumBaseGUISetting.TEXT_SUB_SCALE.get().asFloat(), EnumBaseGUISetting.TEXT_DARK_ENABLED_COLOR.get().asInt()).disableFull());
        this.addElement(this.attachmentWidget = new MessageAttachment(6, 34).disableFull()); 

        this.addElement(this.postageTextLabel = new OxygenTextLabel(6, this.getHeight() - 26, ClientReference.localize("oxygen_mail.gui.mail.postage"), EnumBaseGUISetting.TEXT_SUB_SCALE.get().asFloat(), EnumBaseGUISetting.TEXT_DARK_ENABLED_COLOR.get().asInt()));
        this.addElement(this.postageValue = new OxygenCurrencyValue(6, this.getHeight() - 24)); 
        this.postageValue.setValue(OxygenMain.COMMON_CURRENCY_INDEX, 0L);

        this.addElement(this.confirmButton = new OxygenKeyButton(15, this.getHeight() - 10, ClientReference.localize("oxygen_core.gui.confirm"), Keyboard.KEY_R, this::confirm));
        this.addElement(this.cancelButton = new OxygenKeyButton(this.getWidth() - 55, this.getHeight() - 10, ClientReference.localize("oxygen_core.gui.cancel"), Keyboard.KEY_X, this::close));
    }

    @Override
    public void onOpen() {
        this.attachmentNoticeTextLabel.disableFull();
        this.attachmentWidget.disableFull();
        this.confirmButton.enable();

        this.attachment = null;

        this.type = this.section.getMessageType();
        switch (this.type) {
        case LETTER:
            this.attachment = Attachments.dummy();
            break;
        case REMITTANCE:
            this.attachment = Attachments.remittance(
                    OxygenMain.COMMON_CURRENCY_INDEX, 
                    this.section.getCurrencyValueField().getTypedNumberAsLong());
            break;
        case PARCEL:
            if (this.section.getSelecteItemWrapper() == null) {
                this.confirmButton.disable();
                return;
            }

            this.attachment = Attachments.parcel(
                    this.section.getSelecteItemWrapper(), 
                    (int) this.section.getItemAmountField().getTypedNumberAsLong());
            break;
        case COD:
            if (this.section.getSelecteItemWrapper() == null) {
                this.confirmButton.disable();
                return;
            }

            this.attachment = Attachments.cod(
                    this.section.getSelecteItemWrapper(), 
                    (int) this.section.getItemAmountField().getTypedNumberAsLong(), 
                    OxygenMain.COMMON_CURRENCY_INDEX,
                    (int) this.section.getCurrencyValueField().getTypedNumberAsLong());
            break;
        }
        this.messageTypeTextLabel.setDisplayText(this.type.localizedName());
        this.addresseeTextLabel.setDisplayText(ClientReference.localize("oxygen_mail.gui.mail.addressee", this.section.getAddresseeUsernameField().getTypedText()));
        if (type == EnumMail.LETTER)
            this.attachmentNoticeTextLabel.enableFull();
        else {
            this.attachmentWidget.load(this.type, this.attachment);
            this.attachmentWidget.enableFull();
        }
        long 
        postage = 0L,
        codComission = 0L,
        remittance = 0L,
        value,
        percent;

        this.subject = this.section.getSubjectField().getTypedText();
        this.message = this.section.getMessageBox().getTypedText();

        switch (this.type) {
        case LETTER:
            if (this.message.isEmpty())
                this.confirmButton.disable();
            value = PrivilegesProviderClient.getAsLong(EnumMailPrivilege.LETTER_POSTAGE_VALUE.id(), MailConfig.LETTER_POSTAGE_VALUE.asLong());
            postage = value;
            break;
        case REMITTANCE:
            if (this.attachment.getCurrencyValue() <= 0L)
                this.confirmButton.disable();
            percent = PrivilegesProviderClient.getAsInt(EnumMailPrivilege.REMITTANCE_POSTAGE_PERCENT.id(), MailConfig.REMITTANCE_POSTAGE_PERCENT.asInt());
            remittance = this.attachment.getCurrencyValue();
            postage = MathUtils.percentValueOf(this.attachment.getCurrencyValue(), (int) percent);
            break;
        case PARCEL:
            if (this.attachment.getStackWrapper() == null)
                this.confirmButton.disable();
            if (this.attachment.getItemAmount() <= 0)
                this.confirmButton.disable();
            value = PrivilegesProviderClient.getAsLong(EnumMailPrivilege.PARCEL_POSTAGE_VALUE.id(), MailConfig.PACKAGE_POSTAGE_VALUE.asLong());
            postage = value;
            break;
        case COD:
            if (this.attachment.getCurrencyValue() <= 0L || this.attachment.getStackWrapper() == null || this.attachment.getItemAmount() <= 0)
                this.confirmButton.disable();
            value = PrivilegesProviderClient.getAsLong(EnumMailPrivilege.PARCEL_POSTAGE_VALUE.id(), MailConfig.PACKAGE_POSTAGE_VALUE.asLong());
            percent = PrivilegesProviderClient.getAsInt(EnumMailPrivilege.COD_POSTAGE_PERCENT.id(), MailConfig.COD_POSTAGE_PERCENT.asInt());
            codComission = MathUtils.percentValueOf(this.attachment.getCurrencyValue(), (int) percent);
            postage = value;
            break;
        default:
            break;
        }
        this.postageValue.updateValue(postage + codComission);
        if (remittance + postage > this.section.getBalanceValue().getValue()) {
            this.postageValue.setRed(true);
            this.confirmButton.disable();
        }
        this.postageValue.setX(this.getX() + 8 + this.textWidth(this.postageValue.getDisplayText(), this.postageValue.getTextScale()));
    }

    private void confirm() {
        MailManagerClient.instance().getMailboxManager().sendMessageSynced(this.section.getAddresseeUsernameField().getTypedText(), this.type, this.subject, this.message, this.attachment);
        this.close();
    }

    @Override
    public void handleElementClick(AbstractGUISection section, GUIBaseElement element, int mouseButton) {
        if (mouseButton == 0) { 
            if (element == this.cancelButton)
                this.close();
            else if (element == this.confirmButton)
                this.confirm();
        }
    }
}
