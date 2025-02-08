
export default function Video({src}) {
    return <p><video controls style={{maxWidth: '100%'}}><source src={require('@site/static/media/' + src).default}/></video></p>
}
