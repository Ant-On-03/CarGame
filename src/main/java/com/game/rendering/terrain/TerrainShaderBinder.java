package com.game.rendering.terrain;

import com.badlogic.gdx.graphics.glutils.ShaderProgram;

public class TerrainShaderBinder {

    private final TerrainVisualConfig config;

    public TerrainShaderBinder(TerrainVisualConfig config) {
        this.config = config;
    }

    public void bindGlobalUniforms(ShaderProgram shader) {

        shader.setUniformf(
                "u_lightDir",
                config.lighting().lightDirection.x,
                config.lighting().lightDirection.y,
                config.lighting().lightDirection.z
        );

        shader.setUniformf(
                "u_lightColor",
                config.lighting().lightColor.x,
                config.lighting().lightColor.y,
                config.lighting().lightColor.z
        );

        shader.setUniformf(
                "u_outlineThickness",
                config.outline().getThickness()
        );
    }
}