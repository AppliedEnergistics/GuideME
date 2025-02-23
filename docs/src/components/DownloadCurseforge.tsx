import CurseforgeLogo from './curseforge.svg';

function DownloadCurseforge() {
    return (
        <a href="https://www.curseforge.com/minecraft/mc-mods/guideme" className="button button--outline button--secondary"
           title="Download from Curseforge" aria-label="Download from Curseforge">
            <CurseforgeLogo style={{fill: 'currentcolor', width: '250px'}}/>
        </a>
    );
}

export default DownloadCurseforge;
