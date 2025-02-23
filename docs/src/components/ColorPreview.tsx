import React from 'react';

export interface ColorPreviewProps {
    color: string;
}

function ColorPreview({color}: ColorPreviewProps) {
    return (
        <div style={{width: '16px', height: '16px', backgroundColor: color}}></div>
    );
}

export default ColorPreview;
