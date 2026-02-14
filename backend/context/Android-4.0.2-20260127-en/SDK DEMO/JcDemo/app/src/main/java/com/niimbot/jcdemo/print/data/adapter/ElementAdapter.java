package com.niimbot.jcdemo.print.data.adapter;

import com.google.gson.*;
import com.niimbot.jcdemo.print.model.element.BarCodeElement;
import com.niimbot.jcdemo.print.model.element.Element;
import com.niimbot.jcdemo.print.model.element.GraphElement;
import com.niimbot.jcdemo.print.model.element.ImageElement;
import com.niimbot.jcdemo.print.model.element.LineElement;
import com.niimbot.jcdemo.print.model.element.QrCodeElement;
import com.niimbot.jcdemo.print.model.element.QrCodeWithLogoElement;
import com.niimbot.jcdemo.print.model.element.TextElement;
import com.google.gson.stream.JsonReader;
import java.io.IOException;

public class ElementAdapter extends TypeAdapter<Element> {
    private final Gson gson;

    public ElementAdapter(Gson gson) {
        this.gson = gson;
    }

    @Override
    public void write(com.google.gson.stream.JsonWriter out, Element value) throws IOException {
        // Use gson instance for serialization, use default Gson instance if gson is null
        if (gson != null) {
            gson.getAdapter(Object.class).write(out, value);
        } else {
            new Gson().getAdapter(Object.class).write(out, value);
        }
    }

    @Override
    public Element read(JsonReader reader) throws IOException {
        JsonElement jsonElement = JsonParser.parseReader(reader);
        JsonObject jsonObject = jsonElement.getAsJsonObject();
        
        JsonElement typeElement = jsonObject.get("type");
        if (typeElement == null) {
            throw new JsonParseException("Missing type field");
        }
        
        String typeValue = typeElement.getAsString();
        Class<? extends Element> elementClass = switch (typeValue) {
            case "text" -> TextElement.class;
            case "barCode" -> BarCodeElement.class;
            case "line" -> LineElement.class;
            case "graph" -> GraphElement.class;
            case "qrCode" -> QrCodeElement.class;
            case "qrCodeWithLogo" -> QrCodeWithLogoElement.class;
            case "image" -> ImageElement.class;
            default -> throw new JsonParseException("Unknown type: " + typeValue);
        };
        
        // Use gson instance for deserialization, use default Gson instance if gson is null
        if (gson != null) {
            return gson.fromJson(jsonObject, elementClass);
        } else {
            return new Gson().fromJson(jsonObject, elementClass);
        }
    }
}