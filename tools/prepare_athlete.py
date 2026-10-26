"""Convert the CC0 Quaternius rig to a small, deterministic runtime mesh (no third-party code)."""
from pathlib import Path
import argparse, gzip, json, struct
import numpy as np

def convert(source, output):
    blob=source.read_bytes(); length=struct.unpack_from('<I',blob,12)[0]
    g=json.loads(blob[20:20+length]); binary=blob[28+length:]
    def accessor(index):
        a=g['accessors'][index];v=g['bufferViews'][a['bufferView']]
        dtype={5126:'<f4',5123:'<u2',5125:'<u4',5121:'u1'}[a['componentType']]
        size={'SCALAR':1,'VEC2':2,'VEC3':3,'VEC4':4,'MAT4':16}[a['type']]
        return np.frombuffer(binary,dtype=dtype,count=a['count']*size,
            offset=v.get('byteOffset',0)+a.get('byteOffset',0)).reshape(-1,size).copy()
    parents={c:i for i,n in enumerate(g['nodes']) for c in n.get('children',[])}
    def local(node):
        x,y,z,w=node.get('rotation',[0,0,0,1])
        r=np.array([[1-2*(y*y+z*z),2*(x*y-z*w),2*(x*z+y*w)],
            [2*(x*y+z*w),1-2*(x*x+z*z),2*(y*z-x*w)],
            [2*(x*z-y*w),2*(y*z+x*w),1-2*(x*x+y*y)]])
        m=np.eye(4);m[:3,:3]=r@np.diag(node.get('scale',[1,1,1]));m[:3,3]=node.get('translation',[0,0,0]);return m
    def world(i):return (world(parents[i]) if i in parents else np.eye(4))@local(g['nodes'][i])
    old_joints=g['skins'][0]['joints'];joint_set=set(old_joints)
    order=[]
    def visit(i):
        order.append(i)
        for c in g['nodes'][i].get('children',[]):
            if c in joint_set:visit(c)
    for i in old_joints:
        if parents.get(i) not in joint_set:visit(i)
    mapping={node:i for i,node in enumerate(order)}
    names=[g['nodes'][i]['name'] for i in order]
    rest=np.array([world(i)[:3,3][[2,1,0]] for i in order])
    axes=[]
    for i,node in enumerate(order):
        children=[c for c in g['nodes'][node].get('children',[]) if c in mapping]
        if children:axis=rest[mapping[children[0]]]-rest[i]
        else:axis=rest[i]-rest[mapping[parents[node]]] if parents.get(node) in mapping else np.array([0.,1.,0.])
        axes.append(axis/max(np.linalg.norm(axis),1e-8))
    positions=[];normals=[];joints=[];weights=[];indices=[];parts=[];materials=[];vertex_parts=[];offset=0
    # MusclePart ordinals; body surface regions follow the imported skeleton and rest-space normals.
    SURFACE,PEC,LAT,TRAP,ERECTOR,DELTOID,BICEPS,TRICEPS,FOREARM,ABS,GLUTE,QUAD,HAM,CALF=range(14)
    for mesh_index,mesh in enumerate(g['meshes']):
        for p in mesh['primitives']:
            attributes=p['attributes'];pos=accessor(attributes['POSITION'])
            norm=accessor(attributes['NORMAL']);idx=accessor(p['indices']).reshape(-1,3)
            js=accessor(attributes['JOINTS_0']).astype(int);ws=accessor(attributes['WEIGHTS_0'])
            js=np.vectorize(lambda n:mapping[old_joints[n]])(js)
            # Split triangles at garment hems; classifying whole source triangles creates a jagged edge.
            if mesh_index==2:
                pv=list(pos);nv=list(norm);jv=list(js);wv=list(ws);triangles=idx.tolist();edge_cache={}
                for height in [.095,.705,1.025]:
                    clipped=[]
                    def intersection(a,b):
                        key=(min(a,b),max(a,b),height)
                        if key in edge_cache:return edge_cache[key]
                        t=(height-pv[a][1])/(pv[b][1]-pv[a][1])
                        p=pv[a]*(1-t)+pv[b]*t;p[1]=height
                        n=nv[a]*(1-t)+nv[b]*t;n/=max(np.linalg.norm(n),1e-8)
                        combined={}
                        for source,factor in [(a,1-t),(b,t)]:
                            for bone,weight in zip(jv[source],wv[source]):
                                combined[bone]=combined.get(bone,0)+weight*factor
                        selected=sorted(combined,key=combined.get,reverse=True)[:4]
                        w=np.array([combined[j] for j in selected]);w/=w.sum()
                        w=np.pad(w,(0,4-len(w)));selected+= [0]*(4-len(selected))
                        result=len(pv);pv.append(p);nv.append(n);jv.append(np.array(selected));wv.append(w)
                        edge_cache[key]=result;return result
                    for tri in triangles:
                        ys=[pv[i][1] for i in tri]
                        if min(ys)>=height-1e-7 or max(ys)<=height+1e-7:clipped.append(tri);continue
                        for sign in [1,-1]:
                            polygon=[]
                            for k,a in enumerate(tri):
                                b=tri[(k+1)%3];inside=(pv[a][1]-height)*sign>=0
                                next_inside=(pv[b][1]-height)*sign>=0
                                if inside:polygon.append(a)
                                if inside!=next_inside:polygon.append(intersection(a,b))
                            for k in range(1,len(polygon)-1):clipped.append([polygon[0],polygon[k],polygon[k+1]])
                    triangles=clipped
                pos=np.array(pv);norm=np.array(nv);js=np.array(jv);ws=np.array(wv);idx=np.array(triangles)
            def classify(center,normal,totals):
                x,y,z=center;name=names[int(totals.argmax())];part=SURFACE;material=0
                if mesh_index==0:return part,4
                if mesh_index==1:return part,3
                if 'upperarm' in name:
                    arm=rest[names.index(name)][[2,1,0]]
                    part=DELTOID if np.linalg.norm(center-arm)<.095 else BICEPS if normal[2]>.05 else TRICEPS
                elif 'lowerarm' in name:part=FOREARM
                elif 'thigh' in name:part=GLUTE if y>.89 and normal[2]<-.2 else QUAD if normal[2]>=0 else HAM
                elif 'calf' in name:part=CALF
                elif name in ['pelvis','spine_01','spine_02','spine_03','clavicle_l','clavicle_r']:
                    if abs(x)>.175 and y>1.37:part=DELTOID
                    elif normal[2]>.1:part=PEC if y>1.29 else ABS
                    elif y<1.04:part=GLUTE
                    elif y>1.39:part=TRAP
                    else:part=LAT if abs(x)>.065 else ERECTOR
                if y<1.025 and y>.705 and abs(x)<.26:material=1
                if y<.095:material=2
                return part,material
            for i in range(len(pos)):
                totals=np.zeros(len(order));np.add.at(totals,js[i],ws[i])
                vertex_parts.append(classify(pos[i],norm[i],totals)[0])
            for tri in idx:
                center=pos[tri].mean(0);normal=norm[tri].mean(0);x,y,z=center
                totals=np.zeros(len(order));np.add.at(totals,js[tri].ravel(),ws[tri].ravel())
                part,material=classify(center,normal,totals)
                parts.append(part);materials.append(material)
            positions.append(pos[:,[2,1,0]]);normals.append(norm[:,[2,1,0]])
            joints.append(js);weights.append(ws);indices.append(idx+offset);offset+=len(pos)
    out=bytearray(b'ATH2')
    out+=struct.pack('>III',len(order),offset,len(parts))
    for i,node in enumerate(order):
        name=names[i].encode('ascii');out+=struct.pack('>H',len(name))+name
        out+=struct.pack('>i6f',mapping.get(parents.get(node),-1),*rest[i],*axes[i])
    for collection,dtype in [(positions,'>f4'),(normals,'>f4'),(joints,'>u2'),(weights,'>f4'),(indices,'>u4')]:
        out+=np.concatenate(collection).astype(dtype).tobytes()
    out+=bytes(parts)+bytes(materials)+bytes(vertex_parts)
    output.parent.mkdir(parents=True,exist_ok=True)
    output.write_bytes(gzip.compress(bytes(out),mtime=0))
    print(json.dumps(dict(bones=len(order),vertices=offset,triangles=len(parts),bytes=output.stat().st_size)))

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--source',type=Path,required=True);parser.add_argument('--output',type=Path,required=True)
    args=parser.parse_args();convert(args.source,args.output)
