
import ModrinthLogo from './modrinth.svg';

function DownloadModrinth() {
    return (
        <a href="https://modrinth.com/mod/guideme" className="button button--outline button--secondary"
           title="Download from Modrinth" aria-label="Download from Modrinth">
            <ModrinthLogo style={{width: '250px'}} />
        </a>
    );
}

export default DownloadModrinth;
