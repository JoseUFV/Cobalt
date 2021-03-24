package it.auties.whatsapp4j.response.model;

/**
 * An interface that can be implemented to signal that a class may represent a piece of data sent by WhatsappWeb's WebSocket
 * 
 * This class only allows two types of models:</li>
 * <li>{@link BinaryResponseModel} - a model built from a WhatsappNode </li>
 * <li>{@link JsonResponseModel} - a model built from a JSON String</li>
 */
public sealed interface ResponseModel permits BinaryResponseModel, JsonResponseModel {

}
